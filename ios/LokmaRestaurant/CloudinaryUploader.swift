import UIKit

/// Taom rasmini admin panel bilan BIR XIL yo'l bilan yuklaydi (lib/cloudinary.js):
///   1. serverdan imzo   2. rasm TO'G'RIDAN Cloudinary'ga   3. `secure_url` qaytadi.
/// Rasm 4:3 ga kesiladi (admin ham 4/3), ≤1600 px, ≤5 MB.
enum CloudinaryUploader {
    static func upload(_ image: UIImage, api: APIClient) async throws -> String {
        guard let jpeg = prepare(image) else { throw APIError.http(0, "Rasmni tayyorlab bo'lmadi") }
        let sig = try await api.uploadSignature()
        let boundary = "lg-" + UUID().uuidString
        var body = Data()
        func field(_ name: String, _ value: String) { body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n".utf8)) }
        field("api_key", sig.apiKey); field("timestamp", String(sig.timestamp)); field("signature", sig.signature)
        field("folder", sig.folder); field("allowed_formats", sig.allowedFormats)      // imzoga kiritilgan: AYNAN shu qiymat bo'lishi shart
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"dish.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".utf8))
        body.append(jpeg)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))

        var request = URLRequest(url: URL(string: "https://api.cloudinary.com/v1_1/\(sig.cloudName)/image/upload")!)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        // URLSession.shared, NOT the API client: our login token must never be sent to a third party.
        let (data, response) = try await URLSession.shared.upload(for: request, from: body)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIError.http((response as? HTTPURLResponse)?.statusCode ?? 0, "Rasm yuklanmadi. Qayta urinib ko'ring.")
        }
        guard let url = ((try? JSONSerialization.jsonObject(with: data)) as? [String: Any])?["secure_url"] as? String, !url.isEmpty else {
            throw APIError.http(0, "Rasm manzili qaytmadi")
        }
        return url
    }

    /// Upright (EXIF applied by redrawing), centre-cropped to 4:3, ≤1600 px wide, JPEG ≤5 MB.
    static func prepare(_ image: UIImage) -> Data? {
        let ratio: CGFloat = 4.0 / 3.0
        let w = image.size.width, h = image.size.height
        guard w > 0, h > 0 else { return nil }
        let cropW = w / h > ratio ? h * ratio : w
        let cropH = w / h > ratio ? h : w / ratio
        let outW = min(cropW, 1600), scale = outW / cropW
        let size = CGSize(width: outW, height: cropH * scale)
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let rendered = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            // draw() honours image orientation, so the crop below is taken from the UPRIGHT picture
            image.draw(in: CGRect(x: -(w - cropW) / 2 * scale, y: -(h - cropH) / 2 * scale, width: w * scale, height: h * scale))
        }
        var quality: CGFloat = 0.88
        var data = rendered.jpegData(compressionQuality: quality)
        while let d = data, d.count > 5 * 1024 * 1024, quality > 0.3 { quality -= 0.08; data = rendered.jpegData(compressionQuality: quality) }
        return data
    }
}
