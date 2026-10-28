import UIKit
import UniformTypeIdentifiers

@MainActor final class SecureClipboard {
    private var ownedChangeCount: Int?

    func copy(_ value: String) {
        let board = UIPasteboard.general
        board.setItems([[UTType.utf8PlainText.identifier: value]], options: [
            .localOnly: true,
            .expirationDate: Date().addingTimeInterval(30)
        ])
        ownedChangeCount = board.changeCount
    }

    func clearOwned() {
        let board = UIPasteboard.general
        if board.changeCount == ownedChangeCount { board.items = [] }
        ownedChangeCount = nil
    }
}
