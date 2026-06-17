package vn.medisense.app.api;

/**
 * Response model cho lệnh giọng nói từ Gemini AI.
 *
 * action values:
 *   QUERY_SCHEDULE — hỏi lịch uống thuốc
 *   MARK_TAKEN     — báo đã uống thuốc (medName phải khớp với danh sách)
 *   OPEN_SCREEN    — mở một màn hình cụ thể (screen)
 *   DOSAGE_ADVICE  — hỏi về liều lượng/thay đổi thuốc → bị chặn, trả lời an toàn
 *   UNKNOWN        — không hiểu hoặc không liên quan
 */
public class VoiceCommandResponse {
     public String action;   // "QUERY_SCHEDULE" | "MARK_TAKEN" | "OPEN_SCREEN" | "DOSAGE_ADVICE" | "UNKNOWN"
     public String medName;  // Tên thuốc (chỉ có khi action = "MARK_TAKEN")
     public String screen;   // "ADD_MEDICATION" | "HEALTH" | "EMERGENCY"
     public String answer;   // Câu trả lời tự nhiên bằng tiếng Việt

    public VoiceCommandResponse() {
        this.action  = "UNKNOWN";
        this.answer  = "";
        this.medName = "";
        this.screen  = "";
    }
}
