import SwiftUI

extension Color {
    static let passgenAccent = Color("AccentColor")
    static let passgenOnAccent = Color(uiColor: .systemBackground)
}

struct PrimaryActionStyle: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    func body(content: Content) -> some View {
        if #available(iOS 26, *), !reduceTransparency {
            content.foregroundStyle(Color.passgenOnAccent).buttonStyle(.glassProminent)
        } else {
            content.foregroundStyle(Color.passgenOnAccent).buttonStyle(.borderedProminent)
        }
    }
}
