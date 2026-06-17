package vn.medisense.app.api;

/**
 * Response model cho kết quả kiểm tra tương tác thuốc từ Gemini AI.
 *
 * Prompt yêu cầu Gemini trả về JSON với các field:
 *   hasWarning, severity, interactionSummary, foodWarnings, recommendedAction, disclaimer
 *
 * Tương thích ngược: field "message" cũ vẫn được giữ.
 */
public class DrugInteractionResponse {
    /** Có cảnh báo không */
    public boolean hasWarning;

    /** Mức độ: "low" | "medium" | "high" */
    public String severity;

    /**
     * Tóm tắt tương tác (field mới, ưu tiên hơn message).
     * Dùng ngôn ngữ "có khả năng", "cần kiểm tra", không dùng "chắc chắn nguy hiểm".
     */
    public String interactionSummary;

    /** Tương thích ngược với field cũ */
    public String message;

    /** Thực phẩm cần tránh */
    public String foodWarnings;

    /** Hành động khuyến nghị từ AI */
    public String recommendedAction;

    /** Disclaimer do AI tự thêm vào response */
    public String disclaimer;

    /** Đánh dấu người dùng muốn hỏi bác sĩ (set bởi UI, không phải AI) */
    public transient boolean markedForDoctorConsult = false;

    public DrugInteractionResponse() {
        this.hasWarning          = false;
        this.severity            = "low";
        this.interactionSummary  = "";
        this.message             = "";
        this.foodWarnings        = "";
        this.recommendedAction   = "";
        this.disclaimer          = "";
    }

    public boolean isHighSeverity() {
        return "high".equalsIgnoreCase(severity);
    }

    public boolean isMediumSeverity() {
        return "medium".equalsIgnoreCase(severity);
    }

    /**
     * Trả về nội dung cảnh báo chính — ưu tiên interactionSummary, fallback message.
     */
    public String getMainMessage() {
        if (interactionSummary != null && !interactionSummary.trim().isEmpty()) {
            return interactionSummary;
        }
        return message != null ? message : "";
    }
}
