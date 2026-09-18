// Minimal native wrapper: one window, one WKWebView pointed at the bundled index.html.
import Cocoa
import WebKit

class Delegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminateAfterLastWindowClosed(_ s: NSApplication) -> Bool { true }
}

let app = NSApplication.shared
let delegate = Delegate()
app.delegate = delegate
app.setActivationPolicy(.regular)

let menu = NSMenu()
let appMenu = NSMenu(), editMenu = NSMenu(title: "Edit")
appMenu.addItem(withTitle: "Quit Tatsu", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
editMenu.addItem(withTitle: "Cut", action: #selector(NSText.cut(_:)), keyEquivalent: "x")
editMenu.addItem(withTitle: "Copy", action: #selector(NSText.copy(_:)), keyEquivalent: "c")
editMenu.addItem(withTitle: "Paste", action: #selector(NSText.paste(_:)), keyEquivalent: "v")
editMenu.addItem(withTitle: "Select All", action: #selector(NSText.selectAll(_:)), keyEquivalent: "a")
editMenu.addItem(withTitle: "Close", action: #selector(NSWindow.performClose(_:)), keyEquivalent: "w")
for m in [appMenu, editMenu] { let i = NSMenuItem(); i.submenu = m; menu.addItem(i) }
app.mainMenu = menu

let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 960, height: 720),
                      styleMask: [.titled, .closable, .miniaturizable, .resizable],
                      backing: .buffered, defer: false)
window.title = "Tatsu"
window.center()
window.setFrameAutosaveName("main")
let web = WKWebView(frame: window.contentView!.bounds)
web.autoresizingMask = [.width, .height]
window.contentView!.addSubview(web)
let html = Bundle.main.resourceURL!.appendingPathComponent("index.html")
web.loadFileURL(html, allowingReadAccessTo: html.deletingLastPathComponent())
window.makeKeyAndOrderFront(nil)
app.activate(ignoringOtherApps: true)
app.run()
