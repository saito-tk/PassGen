import SwiftUI

extension Color {
    static let passgenAccent = Color("AccentColor")
    static let passgenOnAccent = Color(uiColor: .systemBackground)
    static let passgenMint = Color(red: 0.70, green: 0.93, blue: 0.82)
}

struct GlassCapsule: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    func body(content: Content) -> some View {
        if #available(iOS 26, *), !reduceTransparency {
            content.glassEffect(.regular.interactive(), in: .capsule)
        } else {
            content.background(Color(uiColor: .secondarySystemGroupedBackground), in: Capsule())
                .overlay(Capsule().strokeBorder(.secondary.opacity(0.2)))
        }
    }
}

struct PrimaryActionStyle: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    func body(content: Content) -> some View {
        if #available(iOS 26, *), !reduceTransparency { content.foregroundStyle(Color.passgenOnAccent).buttonStyle(.glassProminent) }
        else { content.foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent) }
    }
}

struct AppBackdrop: View {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        Color(uiColor: .systemGroupedBackground)
            .overlay {
                if !reduceTransparency {
                    GeometryReader { geometry in
                        Ellipse().fill(Color.passgenAccent.opacity(scheme == .dark ? 0.15 : 0.08))
                            .frame(width: 450, height: 500).blur(radius: 70)
                            .position(x: geometry.size.width, y: 150)
                        Circle().fill(Color.cyan.opacity(0.05)).frame(width: 350).blur(radius: 80)
                            .position(x: 0, y: geometry.size.height * 0.75)
                    }
                }
            }
            .clipped().ignoresSafeArea().accessibilityHidden(true)
    }
}
