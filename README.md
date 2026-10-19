# Peduli Diriku

Aplikasi Android Kotlin + layout XML untuk pencatatan asupan harian. Buka proyek di Android Studio, sinkronkan Gradle, kemudian jalankan konfigurasi `app` (Android 7.0 / API 24 atau lebih baru).

## Fitur versi awal

- Katalog awal 10 makanan/minuman yang umum di Indonesia, pencarian, porsi pecahan, serta katalog pribadi dari label kemasan.
- Penyimpanan SQLite lokal yang bertahan setelah aplikasi ditutup; pencatatan energi, gula total, natrium, dan protein.
- Ringkasan per tanggal dan hapus catatan dengan konfirmasi. Catatan baru dimasukkan ke hari ini; riwayat tetap tersedia setelah pergantian hari.
- Ambang pengingat gula total 50 g. Ini bukan batas medis personal: acuan WHO di bawah membahas **gula bebas**, yang berbeda dari gula total.
- WorldTimeAPI melalui HTTPS, pilihan WIB/WITA/WIT, timeout dan fallback waktu perangkat saat offline. Default WIB. Setelah sinkronisasi, waktu berjalan memakai monotonic elapsed time agar perubahan jam perangkat tidak menggeser sesi tersinkron. Sinkronisasi diulang saat proses aplikasi baru dibuat; worker yang berjalan pada proses baru dapat memakai fallback perangkat.
- WorkManager: pengingat input setiap satu jam setelah penjadwalan, ide sarapan pukul 07.00, serta ringkasan pada 23.30 dalam zona yang dipilih. Klik notifikasi ringkasan membuka tanggal terkait, termasuk bila notifikasi terlambat melewati tengah malam. Ringkasan menggunakan data tanggal tersebut saat worker berjalan; perubahan setelah 23.30 terlihat pada riwayat.
- Izin notifikasi Android 13+ tersedia lewat tombol pengaturan. Pengingat baru terlihat setelah izin diberikan. Pengguna dapat menonaktifkan notifikasi melalui pengaturan Android.

## Arsitektur

`domain/`: model, kontrak repository/jam, dan use case `SummarizeDay`; tanpa dependency Android.

`data/`: implementasi SQLite dan API waktu. `presentation/`: intent MVI → ViewModel → immutable StateFlow → render XML Activity; mutasi diserialkan memakai Mutex. `reminder/`: penjadwalan dan notifikasi. `TrackerApplication` menjadi composition root dependency.

## Batas cakupan

Katalog bawaan adalah **contoh estimasi**, belum merupakan database makanan Indonesia lengkap atau terverifikasi. Nilainya dapat berbeda berdasarkan resep, merek, dan takaran. Sebelum rilis produksi, integrasikan sumber komposisi pangan yang berizin dengan provenance per makanan dan validasi nilai gizinya. Belum ada akun, sinkronisasi cloud, ataupun pemindaian barcode.

WorkManager mempertahankan pekerjaan lintas restart, tetapi waktunya **tidak presisi**; Doze, pembatasan baterai, izin notifikasi, dan force-stop dapat menunda atau menghentikan pengingat. Target jadwal 23.30 bukan jaminan notifikasi tepat menit tersebut. Ringkasan mencakup hari kalender dalam zona terpilih, bukan periode bergulir 24 jam. Data disimpan lokal; mekanisme backup Android mengikuti konfigurasi proyek.

## Verifikasi

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Unit test domain memeriksa porsi pecahan, batas tanggal tengah malam WIB, ambang gula, serta penghapusan. Uji perangkat yang disarankan: tambah makanan, tutup/buka aplikasi, input katalog pribadi, tolak/izinkan notifikasi, ubah zona waktu, dan cek pekerjaan terjadwal melalui WorkManager Inspector.

## Referensi

- WHO healthy diet: https://www.who.int/news-room/fact-sheets/detail/healthy-diet
- WorldTimeAPI: https://worldtimeapi.org/ — endpoint `https://worldtimeapi.org/api/timezone/Asia/Jakarta` (atau Makassar/Jayapura). Ketersediaan layanan tidak dijamin; fallback perangkat ditampilkan di UI.
- Android WorkManager timing: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
