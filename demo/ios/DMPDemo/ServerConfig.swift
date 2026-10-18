//
//  ServerConfig.swift
//  DMPDemo
//
//  服务器配置文件
//  注意: 此文件包含默认配置，本地开发时可在 ServerConfig.local.swift 中覆盖

import Foundation

struct ServerConfig {
    // DMP 服务运行在设备本地，默认无需修改
    private static let defaultHost = "127.0.0.1"
    private static let defaultPort = "28473"
    
    // 使用本地配置（如果存在）
    static var host: String {
        ServerConfigLocal.host
    }
    
    static var port: String {
        ServerConfigLocal.port
    }
    
    static var baseUrl: String {
        "http://\(host):\(port)"
    }
}