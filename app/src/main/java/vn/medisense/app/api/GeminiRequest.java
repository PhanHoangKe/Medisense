package vn.medisense.app.api;

import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.List;

/**
 * Request model gửi đến Gemini API
 * Chứa nội dung prompt để AI xử lý
 */
public class GeminiRequest {
    public List<Content> contents;

    /**
     * Tạo request với văn bản prompt
     * 
     * @param text Văn bản prompt gửi cho AI
     */
    public GeminiRequest(@NonNull String text) {
        this.contents = Collections.singletonList(new Content(text));
    }

    /**
     * Nội dung bao gồm nhiều phần (parts)
     */
    public static class Content {
        public List<Part> parts;

        public Content(@NonNull String text) {
            this.parts = Collections.singletonList(new Part(text));
        }
    }

    /**
     * Mỗi part chứa văn bản
     */
    public static class Part {
        public String text;

        public Part(@NonNull String text) {
            this.text = text;
        }
    }
}
