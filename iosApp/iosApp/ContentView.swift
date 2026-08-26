import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // A book sent to Lukia from another app - the Share Sheet, "Open in",
            // a tap in Files. The shell passes the URL on and forgets it: what
            // arrives is readable only inside a security scope, so the Kotlin
            // side takes its own copy straight away.
            .onOpenURL { url in
                IOSIncomingDocumentsKt.deliverIncomingDocument(url: url.absoluteString)
            }
    }
}
