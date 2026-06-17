package vn.medisense.app.api;

import androidx.annotation.Nullable;
import java.util.List;

public class RawGeminiPrescriptionResult {
    @Nullable
    public String diagnosis;

    @Nullable
    public String doctorAdvice;

    @Nullable
    public List<ParsedPrescriptionItem> medications;
}
