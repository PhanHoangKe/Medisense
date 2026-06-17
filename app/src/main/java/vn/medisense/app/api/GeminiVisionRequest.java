package vn.medisense.app.api;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Request model cho Gemini Multimodal (text + image)
 */
public class GeminiVisionRequest {
    public List<Content> contents = new ArrayList<>();

    public GeminiVisionRequest(@NonNull String promptText, @NonNull String base64Image, @NonNull String mimeType) {
        Content content = new Content();
        content.parts.add(Part.textPart(promptText));
        content.parts.add(Part.inlineImage(base64Image, mimeType));
        contents.add(content);
    }

    public static class Content {
        public List<Part> parts = new ArrayList<>();
    }

    public static class Part {
        public String text;
        public InlineData inlineData;

        public static Part textPart(@NonNull String text) {
            Part part = new Part();
            part.text = text;
            return part;
        }

        public static Part inlineImage(@NonNull String base64, @NonNull String mimeType) {
            Part part = new Part();
            part.inlineData = new InlineData(base64, mimeType);
            return part;
        }
    }

    public static class InlineData {
        public String data;
        public String mimeType;

        public InlineData(@NonNull String data, @NonNull String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }
    }
}

