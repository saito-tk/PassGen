import AppKit

// Repo-native geometric monogram. No fonts, generated imagery or external asset dependency.
let size = 1024
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
                              bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
                              isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
NSColor(red: 0.07, green: 0.24, blue: 0.29, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: size, height: size)).fill()
let path = NSBezierPath()
path.move(to: NSPoint(x: 290, y: 240))
path.line(to: NSPoint(x: 290, y: 780))
path.line(to: NSPoint(x: 550, y: 780))
path.curve(to: NSPoint(x: 770, y: 560), controlPoint1: NSPoint(x: 700, y: 780), controlPoint2: NSPoint(x: 770, y: 710))
path.curve(to: NSPoint(x: 550, y: 340), controlPoint1: NSPoint(x: 770, y: 410), controlPoint2: NSPoint(x: 700, y: 340))
path.line(to: NSPoint(x: 445, y: 340))
path.line(to: NSPoint(x: 445, y: 240)); path.close()
path.move(to: NSPoint(x: 445, y: 490)); path.line(to: NSPoint(x: 540, y: 490))
path.curve(to: NSPoint(x: 615, y: 560), controlPoint1: NSPoint(x: 590, y: 490), controlPoint2: NSPoint(x: 615, y: 510))
path.curve(to: NSPoint(x: 540, y: 630), controlPoint1: NSPoint(x: 615, y: 610), controlPoint2: NSPoint(x: 590, y: 630))
path.line(to: NSPoint(x: 445, y: 630)); path.close()
path.windingRule = .evenOdd
NSColor(red: 0.72, green: 0.95, blue: 0.85, alpha: 1).setFill(); path.fill()
NSGraphicsContext.restoreGraphicsState()
try bitmap.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
