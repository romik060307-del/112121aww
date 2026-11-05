# OHLI Social — Android Client v1.0.3

Proyek ini membuat **APK klien OHLI Social**. APK membuka website/server OHLI yang sudah berjalan melalui Android WebView. **Server tidak dibundel, tidak dijalankan, dan tidak diubah oleh proyek ini.**

## Yang sudah disiapkan

- Nama aplikasi: **OHLI Social**.
- Membuka alamat OHLI langsung di dalam aplikasi, bukan melempar halaman utama ke Chrome.
- Splash screen saat aplikasi menghubungkan ke server.
- JavaScript, DOM storage, cookie, dan cache WebView untuk kebutuhan aplikasi web.
- Izin kamera dan mikrofon diminta saat fitur membutuhkannya; akses WebRTC dibatasi ke origin HTTPS halaman OHLI yang sedang terbuka atau alamat awal yang dikonfigurasi.
- Pemilih berkas dari Android untuk upload foto/video/dokumen yang diminta website.
- Tombol kembali Android mengikuti riwayat halaman, lalu mencoba memilih tab Beranda; bila tab tidak terdeteksi, aplikasi kembali memuat alamat awal OHLI. Tombol Back tidak menutup APK secara tidak sengaja.
- Tautan HTTPS di luar domain OHLI dibuka dengan aplikasi eksternal; skema berisiko diblokir.

## Alamat server

Alamat saat ini disetel di `app/build.gradle` melalui `OHLI_URL`:

`https://towers-professor-serial-recordings.trycloudflare.com/`

Bila alamat tunnel berubah, ubah nilai `OHLI_URL` sebelum build. Tunnel Cloudflare quick-tunnel harus tetap aktif agar teman-teman bisa membuka server dari internet. Untuk pemakaian publik jangka panjang, pakai hostname/domain tetap.

## Membuat APK melalui GitHub Actions

1. Buat repository GitHub baru.
2. Upload seluruh isi folder proyek ini (folder `app`, `.github`, dan file Gradle) ke root repository.
3. Buka tab **Actions**.
4. Pilih workflow **Build OHLI Social Client APK** lalu tekan **Run workflow**.
5. Setelah workflow selesai, buka hasil run dan unduh artifact **OHLI-Social-Client-APK**.
6. Ekstrak artifact ZIP; file yang dipasang di Android adalah `app-debug.apk`.

Workflow menghasilkan **debug APK** untuk uji coba dan pemasangan langsung. Untuk rilis Play Store atau distribusi release resmi, perlu konfigurasi signing key release yang disimpan sebagai GitHub Secrets.

## Batas fitur yang perlu diketahui

APK ini adalah pembungkus klien Android, bukan implementasi ulang semua fitur secara native. Chat, Story, Reels, Feed, musik, login, dan data tetap berasal dari website/backend OHLI yang ada. Integrasi kamera/mikrofon WebRTC dan pemilih file disediakan dari sisi Android, tetapi keberhasilan fitur tetap bergantung pada implementasi website, HTTPS, izin runtime Android, versi Android System WebView, ketersediaan perangkat kamera/mikrofon, dan server.

- Cache WebView membantu memuat ulang sebagian aset, tetapi **bukan** database chat offline dan tidak membuat pesan otomatis tersinkron ketika internet kembali.
- Izin notifikasi Android saja tidak menghasilkan push notification. Push chat/panggilan/follow memerlukan layanan push (misalnya FCM), token perangkat, dan integrasi event di backend. Itu belum ditambahkan agar server tetap tidak diubah.
- Autoplay dengan suara dapat dibatasi kebijakan Android/WebView dan interaksi pengguna; pengaturan aplikasi tidak bisa menjamin semua perangkat mengizinkannya.
- Server dan tunnel harus aktif dan dapat dijangkau. APK tidak membawa salinan server.

## Persyaratan build

- JDK 17
- Android SDK Platform 35 dan Build Tools 35.0.0
- Gradle 8.9
- Java compatibility: `JavaVersion.VERSION_1_8` (kompatibel dengan Gradle 8.9; jangan gunakan `JavaVersion.VERSION_8`)
