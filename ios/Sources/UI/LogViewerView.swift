import SwiftUI

#if DEBUG

/// 日志查看器视图（仅Debug模式可用）
struct LogViewerView: View {
    @State private var serverURL: String = ""
    @State private var qrImage: Image?
    @State private var statusMessage: String = "正在启动服务器..."
    @State private var isServerRunning = false
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // 状态提示
                    Text(statusMessage)
                        .font(.headline)
                        .multilineTextAlignment(.center)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                    
                    // 二维码
                    if let qrImage = qrImage {
                        qrImage
                            .resizable()
                            .scaledToFit()
                            .frame(width: 250, height: 250)
                            .padding()
                            .background(Color.white)
                            .cornerRadius(12)
                            .shadow(radius: 5)
                    }
                    
                    // URL显示
                    if !serverURL.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("服务器地址：")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            
                            Text(serverURL)
                                .font(.system(.body, design: .monospaced))
                                .foregroundColor(.green)
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(.systemGray6))
                                .cornerRadius(8)
                        }
                        .padding(.horizontal)
                    }
                    
                    // 使用说明
                    VStack(alignment: .leading, spacing: 12) {
                        Text("使用说明")
                            .font(.headline)
                            .foregroundColor(.green)
                        
                        VStack(alignment: .leading, spacing: 8) {
                            Text("1. 确保电脑和iPhone在同一WiFi网络")
                            Text("2. 电脑浏览器扫码")
                            Text("3. 实时查看日志（自动刷新）")
                            Text("4. 支持过滤：全部/错误/警告/信息")
                            Text("5. 可导出日志为JSON格式")
                        }
                        .font(.subheadline)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                    }
                    .padding(.horizontal)
                    
                    // 安全提示
                    VStack(alignment: .leading, spacing: 12) {
                        Text("⚠️ 安全提示")
                            .font(.headline)
                            .foregroundColor(.yellow)
                        
                        VStack(alignment: .leading, spacing: 8) {
                            Text("• 仅Debug模式可用")
                            Text("• Release模式自动禁用")
                            Text("• 仅限同WiFi局域网访问")
                            Text("• 发布正式版时请关闭此功能")
                        }
                        .font(.subheadline)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.systemGray5))
                        .cornerRadius(12)
                    }
                    .padding(.horizontal)
                }
                .padding()
            }
            .navigationTitle("日志查看器")
            .onAppear(perform: setupServer)
        }
    }
    
    private func setupServer() {
        #if DEBUG
        let url = DMPLogServer.shared.startServer()
        
        if url.isEmpty {
            statusMessage = "❌ 服务器启动失败"
            return
        }
        
        serverURL = url
        isServerRunning = true
        statusMessage = "✅ 服务器运行中\n\n电脑和手机在同一WiFi下\n扫码即可查看日志"
        
        // 生成二维码
        if let qrCode = generateQRCode(from: url) {
            qrImage = Image(uiImage: qrCode)
        } else {
            statusMessage = "⚠️ 二维码生成失败"
        }
        #else
        statusMessage = "Release模式，日志查看功能已禁用"
        #endif
    }
    
    private func generateQRCode(from string: String) -> UIImage? {
        let data = string.data(using: String.Encoding.ascii)
        
        if let filter = CIFilter(name: "CIQRCodeGenerator") {
            filter.setValue(data, forKey: "inputMessage")
            filter.setValue("H", forKey: "inputCorrectionLevel") // High error correction
            
            let transform = CGAffineTransform(scaleX: 10, y: 10)
            
            if let output = filter.outputImage?.transformed(by: transform) {
                let context = CIContext()
                if let cgImage = context.createCGImage(output, from: output.extent) {
                    return UIImage(cgImage: cgImage)
                }
            }
        }
        
        return nil
    }
}

#endif