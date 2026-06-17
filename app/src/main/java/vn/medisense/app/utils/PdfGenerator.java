package vn.medisense.app.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MedicationDao;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfGenerator {

    public static void generateAndShareReport(Context context) {
        // Chạy tạo trên background thread
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                MedicationDao dao = AppDatabase.getInstance(context).medicationDao();

                // Lấy timestamps cho 30 ngày qua
                Calendar cal = Calendar.getInstance();
                long endTime = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, -30);
                long startTime = cal.getTimeInMillis();

                List<Medication> allMedications = dao.getAllMedicationsSync();

                // Khởi tạo tài liệu PDF
                PdfDocument document = new PdfDocument();
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // Kích thước khổ giấy A4
                PdfDocument.Page page = document.startPage(pageInfo);

                Canvas canvas = page.getCanvas();
                Paint paint = new Paint();

                // Vẽ Tiêu đề (Header)
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(24f);
                paint.setColor(Color.BLACK);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("BÁO CÁO TUÂN THỦ THUỐC", pageInfo.getPageWidth() / 2f, 80, paint);

                // Vẽ Thông tin chung
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setTextSize(14f);
                paint.setTextAlign(Paint.Align.LEFT);

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                String dateRange = "Thời gian: " + sdf.format(new Date(startTime)) + " đến "
                        + sdf.format(new Date(endTime));
                canvas.drawText("Người dùng: Thành Viên MediSense", 50, 140, paint);
                canvas.drawText(dateRange, 50, 170, paint);

                // Vẽ Tiêu đề Cột
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Tên thuốc",    50,  220, paint);
                canvas.drawText("Đã uống",      230, 220, paint);
                canvas.drawText("Bỏ lỡ",        295, 220, paint);
                canvas.drawText("Bỏ qua",       350, 220, paint);
                canvas.drawText("Tổng",         410, 220, paint);
                canvas.drawText("Tuân thủ",     460, 220, paint);

                canvas.drawLine(50, 230, pageInfo.getPageWidth() - 50, 230, paint);

                int yPosition = 260;
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

                for (Medication med : allMedications) {
                    int total   = dao.getTotalRemindersForMedicationSync(med.id, startTime, endTime);
                    int taken   = dao.getTakenRemindersForMedicationSync(med.id, startTime, endTime);
                    int missed  = dao.getMissedRemindersForMedicationSync(med.id, startTime, endTime);
                    int skipped = dao.getSkippedRemindersForMedicationSync(med.id, startTime, endTime);

                    if (total == 0) continue;

                    // Tỷ lệ tuân thủ = đã uống / (đã uống + bỏ lỡ + bỏ qua)
                    int denominator = taken + missed + skipped;
                    int percentage  = denominator > 0
                            ? (int) (((float) taken / denominator) * 100) : 0;

                    // Cắt tên thuốc nếu quá dài
                    String medName = med.name.length() > 22
                            ? med.name.substring(0, 20) + "…" : med.name;

                    canvas.drawText(medName,               50,  yPosition, paint);
                    canvas.drawText(String.valueOf(taken),  230, yPosition, paint);
                    canvas.drawText(String.valueOf(missed), 295, yPosition, paint);
                    canvas.drawText(String.valueOf(skipped),350, yPosition, paint);
                    canvas.drawText(String.valueOf(total),  410, yPosition, paint);

                    if (percentage >= 80)
                        paint.setColor(androidx.core.content.ContextCompat.getColor(
                                context, vn.medisense.app.R.color.status_success));
                    else if (percentage >= 50)
                        paint.setColor(androidx.core.content.ContextCompat.getColor(
                                context, vn.medisense.app.R.color.status_warning));
                    else
                        paint.setColor(androidx.core.content.ContextCompat.getColor(
                                context, vn.medisense.app.R.color.status_error));

                    canvas.drawText(percentage + "%", 460, yPosition, paint);
                    paint.setColor(Color.BLACK);

                    yPosition += 40;
                    if (yPosition > pageInfo.getPageHeight() - 50) break;
                }

                document.finishPage(page);

                // Lưu PDF vào cache
                File reportDir = new File(context.getCacheDir(), "reports");
                if (!reportDir.exists())
                    reportDir.mkdirs();

                File pdfFile = new File(reportDir, "BaoCao_MediSense.pdf");
                try {
                    document.writeTo(new FileOutputStream(pdfFile));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                document.close();

                // Đảm bảo thao tác UI (lựa chọn intent) được chạy an toàn trên Main Thread
                AppExecutors.getInstance().mainThread().execute(() -> sharePdf(context, pdfFile));

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void sharePdf(Context context, File pdfFile) {
        if (pdfFile.exists()) {
            Uri pdfUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pdfFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Báo Cáo Sức Khỏe MediSense");

            Intent chooser = Intent.createChooser(shareIntent, "Chia sẻ báo cáo qua...");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        }
    }
}
