package vn.medisense.app.models;

public class RiskReport {
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    private RiskLevel level;
    private String advice;
    private String disclaimer;
    private double adherenceRate;
    private String details;

    public RiskReport(RiskLevel level, String advice, double adherenceRate, String details) {
        this.level = level;
        this.advice = advice;
        this.adherenceRate = adherenceRate;
        this.details = details;
        this.disclaimer = "Kết quả chỉ mang tính tham khảo, không thay thế tư vấn của bác sĩ hoặc dược sĩ.";
    }

    public RiskLevel getLevel() {
        return level;
    }

    public String getAdvice() {
        return advice;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public double getAdherenceRate() {
        return adherenceRate;
    }

    public String getDetails() {
        return details;
    }
}
