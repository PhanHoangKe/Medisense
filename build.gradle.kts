// File build cấp cao nhất nơi bạn có thể thêm các tùy chọn cấu hình chung cho tất cả các dự án con/module.
plugins {
    alias(libs.plugins.android.application) apply false
}