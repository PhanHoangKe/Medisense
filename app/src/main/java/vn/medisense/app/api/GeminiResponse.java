package vn.medisense.app.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * Response model từ Gemini API
 * Chứa danh sách các candidate (kết quả sinh ra từ AI)
 */
public class GeminiResponse {
    public List<Candidate> candidates;

    /**
     * Một candidate chứa nội dung được sinh ra
     */
    public static class Candidate {
        public Content content;
    }

    /**
     * Nội dung bao gồm nhiều phần (parts)
     */
    public static class Content {
        public List<Part> parts;
    }

    /**
     * Mỗi part chứa văn bản
     */
    public static class Part {
        public String text;
    }

    /**
     * Lấy văn bản đầu tiên từ response
     * Xử lý null safety để tránh NullPointerException
     * 
     * @return Văn bản đầu tiên hoặc null nếu không có
     */
    @Nullable
    public String getFirstText() {
        // Kiểm tra từng cấp để tránh NullPointerException
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        
        Candidate firstCandidate = candidates.get(0);
        if (firstCandidate == null || firstCandidate.content == null) {
            return null;
        }
        
        if (firstCandidate.content.parts == null || firstCandidate.content.parts.isEmpty()) {
            return null;
        }
        
        Part firstPart = firstCandidate.content.parts.get(0);
        if (firstPart == null) {
            return null;
        }
        
        return firstPart.text;
    }
}
