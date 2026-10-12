# ساخت APK بدون Android Studio (روی سرورهای GitHub)

1. در github.com حساب بسازید و یک Repository جدید (Private) با نام دلخواه، مثلاً sms-gateway، ایجاد کنید.
2. محتوای پوشه پروژه (نه خود پوشه) را با «Add file → Upload files» آپلود کنید؛ باید فایل settings.gradle مستقیماً در ریشه Repository باشد.
3. فایل ساخت را بسازید: «Add file → Create new file»، در کادر نام دقیقاً بنویسید
   .github/workflows/build.yml
   و محتوای فایل build.yml را در آن بچسبانید، سپس Commit.
4. تب Actions → «Build APK» → Run workflow. حدود ۵ تا ۱۰ دقیقه صبر کنید.
5. روی اجرای تمام‌شده بزنید، پایین صفحه در بخش Artifacts فایل sms-gateway-apk را دانلود کنید (zip است؛ داخلش app-debug.apk است).
6. APK را روی گوشی نصب کنید.
