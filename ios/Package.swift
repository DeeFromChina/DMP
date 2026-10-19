// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DMP",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "DMP",
            targets: ["DMP"]
        ),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "DMP",
            dependencies: [],
            path: "Sources",
            linkerSettings: [
                .linkedFramework("Metal"),
                .linkedFramework("CoreML"),
            ]
        ),
        .testTarget(
            name: "DMPTests",
            dependencies: ["DMP"],
            path: "Tests"
        ),
    ]
)