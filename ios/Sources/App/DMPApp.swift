import SwiftUI

@main
struct DMPApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        TabView {
            ChatView()
                .tabItem {
                    Label("聊天", systemImage: "message.fill")
                }
            
            ConfigView()
                .tabItem {
                    Label("配置", systemImage: "gearshape.fill")
                }
            
            #if DEBUG
            LogViewerView()
                .tabItem {
                    Label("日志", systemImage: "doc.text.fill")
                }
            #endif
        }
    }
}