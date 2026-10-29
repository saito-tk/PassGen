import UIKit
import UniformTypeIdentifiers

@MainActor final class SecureClipboard {
    private var ownedChangeCount: Int?

    func copy(_ value: String, settings: SecuritySettings = SecuritySettings()) {
        let board = UIPasteboard.general
        var options: [UIPasteboard.OptionsKey: Any] = [.localOnly: !settings.allowUniversalClipboard]
        if settings.autoClearClipboard { options[.expirationDate] = Date().addingTimeInterval(TimeInterval(settings.sanitized.clipboardSeconds)) }
        board.setItems([[UTType.utf8PlainText.identifier: value]], options: options)
        ownedChangeCount = board.changeCount
    }

    func clearOwned() {
        let board = UIPasteboard.general
        if board.changeCount == ownedChangeCount { board.items = [] }
        ownedChangeCount = nil
    }
}
