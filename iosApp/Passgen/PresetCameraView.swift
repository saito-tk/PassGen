import SwiftUI
import AVFoundation
import PassgenCore

struct PresetCameraView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var message: String?
    @State private var denied = false
    @State private var retry = 0
    let detected: (String) -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                PresetCameraPreview(retry: retry, detected: detected) { message, denied in
                    self.message = message; self.denied = denied
                }
                .background(.black).clipShape(RoundedRectangle(cornerRadius: 20))
                .overlay { if let message { Text(message).foregroundStyle(.white).padding(24).background(.black.opacity(0.8)) } }
                Text("プリセットのQRコードをカメラに向けてください。").font(.headline)
                Text("このアプリ用のQRだけを端末内で読み取ります。複数ある場合は1つずつ映してください。")
                    .font(.subheadline).foregroundStyle(.secondary)
                if denied {
                    Button("設定を開く", systemImage: "gearshape") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                } else if message != nil {
                    Button("再試行", systemImage: "arrow.clockwise") { retry += 1 }
                }
            }.padding().accessibilityIdentifier("preset-qr-camera")
                .navigationTitle("カメラで読み取る").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) {
                    Button("カメラを閉じる", systemImage: "xmark") { dismiss() }.labelStyle(.iconOnly)
                } }
        }
    }
}

private struct PresetCameraPreview: UIViewControllerRepresentable {
    let retry: Int
    let detected: (String) -> Void
    let status: (String?, Bool) -> Void
    func makeUIViewController(context: Context) -> PresetCameraController {
        PresetCameraController(detected: detected, status: status)
    }
    func updateUIViewController(_ controller: PresetCameraController, context: Context) { controller.retry(retry) }
    static func dismantleUIViewController(_ controller: PresetCameraController, coordinator: ()) { controller.finish() }
}

@MainActor private final class PresetCameraController: UIViewController {
    private let detected: (String) -> Void
    private let status: (String?, Bool) -> Void
    private var worker: PresetCameraSession!
    private var preview: AVCaptureVideoPreviewLayer!
    private var visible = false
    private var delivered = false
    private var authorizing = false
    private var retryValue = 0
    private var generation = UUID()

    init(detected: @escaping (String) -> Void, status: @escaping (String?, Bool) -> Void) {
        self.detected = detected; self.status = status
        super.init(nibName: nil, bundle: nil)
        worker = PresetCameraSession { [weak self] text, generation in
            guard let self, self.visible, !self.delivered, self.generation == generation, UIApplication.shared.applicationState == .active else { return }
            self.delivered = true; self.worker.stop(); self.detected(text)
        } failed: { [weak self] in guard let self, self.visible else { return }; self.status("カメラを開始できませんでした。画像からの読み取りも利用できます。", false) }
        started: { [weak self] in self?.view.setNeedsLayout() }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        preview = AVCaptureVideoPreviewLayer(session: worker.session); preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        // UIApplication lifecycle notifications are delivered on the main thread.
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews(); preview.frame = view.bounds
        let angle: CGFloat = switch view.window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: 0; case .landscapeRight: 180; case .portraitUpsideDown: 270; default: 90
        }
        if let connection = preview.connection, connection.isVideoRotationAngleSupported(angle) { connection.videoRotationAngle = angle }
    }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); visible = true; begin() }
    override func viewWillDisappear(_ animated: Bool) { finish(); super.viewWillDisappear(animated) }
    func finish() { visible = false; generation = UUID(); worker.stop() }
    func retry(_ value: Int) { if value != retryValue { retryValue = value; begin() } }
    @objc private func background() { generation = UUID(); worker.stop() }
    @objc private func foreground() { begin() }
    private func begin() {
        guard visible, !delivered, UIApplication.shared.applicationState == .active else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: generation = UUID(); status(nil, false); worker.start(generation: generation)
        case .notDetermined:
            guard !authorizing else { return }; authorizing = true
            AVCaptureDevice.requestAccess(for: .video) { [weak self] _ in
                Task { @MainActor in self?.authorizing = false; self?.begin() }
            }
        default: status("QRを読み取るには、カメラへのアクセスを許可してください。", true)
        }
    }
}

/// AVFoundation configuration, start/stop and metadata parsing stay on one serial queue.
/// Only bounded QR strings cross to the main actor; no frame is recorded or retained.
private final class PresetCameraSession: NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "jp.passgen.preset-camera")
    private let detected: @MainActor @Sendable (String, UUID) -> Void
    private let failed: @MainActor @Sendable () -> Void
    private let started: @MainActor @Sendable () -> Void
    private var configured = false
    private var active = false
    private var generation = UUID()
    private var errorObserver: NSObjectProtocol?
    init(detected: @escaping @MainActor @Sendable (String, UUID) -> Void, failed: @escaping @MainActor @Sendable () -> Void, started: @escaping @MainActor @Sendable () -> Void) {
        self.detected = detected; self.failed = failed; self.started = started; super.init()
        errorObserver = NotificationCenter.default.addObserver(forName: AVCaptureSession.runtimeErrorNotification, object: session, queue: nil) { [weak self] _ in
            guard let self else { return }; Task { @MainActor in self.failed() }
        }
    }
    deinit { if let errorObserver { NotificationCenter.default.removeObserver(errorObserver) } }
    func start(generation: UUID) { queue.async { [self] in
        active = true; self.generation = generation
        guard configure() else { Task { @MainActor in failed() }; return }
        session.outputs.compactMap { $0 as? AVCaptureMetadataOutput }.forEach { $0.setMetadataObjectsDelegate(self, queue: queue) }
        if !session.isRunning { session.startRunning() }
        if !session.isRunning { Task { @MainActor in failed() } }
        else { Task { @MainActor in started() } }
    } }
    func stop() { queue.async { [self] in
        active = false; if session.isRunning { session.stopRunning() }
        session.outputs.compactMap { $0 as? AVCaptureMetadataOutput }.forEach { $0.setMetadataObjectsDelegate(nil, queue: nil) }
    } }
    private func configure() -> Bool {
        if configured { return true }
        session.beginConfiguration(); defer { session.commitConfiguration() }
        session.inputs.forEach { session.removeInput($0) }; session.outputs.forEach { session.removeOutput($0) }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) ?? AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) else { return false }
        if session.canSetSessionPreset(.hd1280x720) { session.sessionPreset = .hd1280x720 }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return false }
        session.addOutput(output)
        guard output.availableMetadataObjectTypes.contains(.qr) else { return false }
        output.metadataObjectTypes = [.qr]; output.setMetadataObjectsDelegate(self, queue: queue)
        configured = true; return true
    }
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard active else { return }
        let texts = objects.compactMap { ($0 as? AVMetadataMachineReadableCodeObject)?.stringValue }
        guard let text = PresetQrCodec.shared.selectCandidate(texts: texts) else { return }
        let generation = generation
        Task { @MainActor in detected(text, generation) }
    }
}
