# درگاه پیامک تعمیرجا (Android)

برنامه، پیامک‌های صف CRM را با سیم‌کارت همین گوشی می‌فرستد.

## ساخت APK بدون Android Studio (با GitHub Actions)
1. در github.com حساب بسازید (فقط با ایمیل).
2. New repository → نام دلخواه (مثلاً sms-gateway) → Public → Create.
3. «uploading an existing file» را بزنید و **محتویات** پوشه پروژه (نه خود پوشه) را بکشید و رها کنید:
   `app`, `gradle`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`, `.gitignore`
   (اگر پوشه مخفی `.github` آپلود نشد: Add file → Create new file → نام `.github/workflows/build.yml` را بنویسید و متن فایل `build.yml` را داخلش بچسبانید.)
4. Commit changes.
5. تب **Actions** → «Build APK» → Run workflow (اگر خودکار اجرا نشد).
6. بعد از حدود ۵ تا ۱۰ دقیقه، روی اجرای سبز کلیک کنید → پایین صفحه **Artifacts** → `sms-gateway-apk` را دانلود کنید (zip است؛ داخلش `app-debug.apk`).
7. APK را روی گوشی نصب کنید.

اگر اجرا قرمز شد، متن خطای قرمز را برای من بفرستید.

## تنظیم روی گوشی
- آدرس سایت: `https://tamirja.ir` (بدون / در انتها)
- کلید: همان کلید صفحه «پیامک از طریق گوشی» در CRM
- «آزمایش اتصال» → «شروع»
- اجازه ارسال پیامک، وضعیت تلفن و اعلان‌ها را بدهید؛ «استثنا از بهینه‌سازی باتری» را بزنید.
