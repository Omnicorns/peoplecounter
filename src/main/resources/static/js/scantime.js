/* =========================================================================
   Tab Durasi Pelayanan.

   Berdiri sendiri, tidak menyentuh app.js. Data diambil dari /api/scantime --
   bukan /api/dashboard -- dan baru diambil saat tabnya dibuka pertama kali.

   Penyembunyian dan penampilan panelnya tetap diurus app.js, lewat pasangan
   data-tab="scantime" di menu dan data-panel="scantime" di panel.
   ========================================================================= */
(function () {
    var G = window.Grafik;
    if (!G) return;

    var panel  = document.querySelector('.tab[data-panel="scantime"]');
    var tautan = document.querySelector('#tabBar a[data-tab="scantime"]');
    if (!panel || !tautan) return;

    var data = null;
    var sedangMuat = false;
    var filterTerisi = false;

    var elKpi   = document.getElementById('kpiScan');
    var elPesan = document.getElementById('scanPesan');
    var fTahun  = document.getElementById('stTahun');
    var fBulan  = document.getElementById('stBulan');
    var fLokasi = document.getElementById('stLokasi');

    /* Milik app.js, letaknya di luar semua panel tab. Filter dan KPI POS tidak
       berlaku di sini -- tab ini punya filter dan KPI-nya sendiri. */
    var milikPos = [document.getElementById('kpiBaris'),
                    document.getElementById('formFilter')];

    var BULAN = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
                 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];

    /* ---------------- tampilan durasi ---------------- */

    /* Satuan kolom sumbernya ditentukan server lewat meta.satuan. Kalau detik,
       angka besar dibaca lebih enak sebagai menit dan detik. */
    function durasi(v) {
        if (v === null || v === undefined || isNaN(v)) return '-';
        var satuan = (data && data.meta && data.meta.satuan) || 'detik';
        if (satuan !== 'detik') return G.desimal(v, 1) + ' ' + satuan;
        if (v < 60) return G.desimal(v, 1) + ' dtk';
        var m = Math.floor(v / 60), d = Math.round(v % 60);
        return m + ' mnt ' + (d < 10 ? '0' : '') + d + ' dtk';
    }

    /* ---------------- pengambilan ---------------- */

    function alamat(paksa) {
        var p = new URLSearchParams();
        if (fTahun  && fTahun.value)  p.set('tahun',  fTahun.value);
        if (fBulan  && fBulan.value)  p.set('bulan',  fBulan.value);
        if (fLokasi && fLokasi.value) p.set('lokasi', fLokasi.value);
        if (paksa) p.set('muatUlang', 'true');
        return '/api/scantime?' + p.toString();
    }

    function muat(paksa) {
        if (sedangMuat) return;
        sedangMuat = true;
        pesan('Menghitung durasi pelayanan…', false);

        fetch(alamat(paksa), { headers: { 'Accept': 'application/json' } })
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (j) {
                sedangMuat = false;
                if (j.error) { pesan(j.error + (j.rinci ? ' (' + j.rinci + ')' : ''), true); return; }
                data = j;
                pesan('', false);
                isiFilter();
                gambar();
            })
            .catch(function (e) {
                sedangMuat = false;
                pesan('Tidak bisa menghubungi server aplikasi. ' + e.message, true);
            });
    }

    function pesan(teks, gagal) {
        if (!elPesan) return;
        elPesan.textContent = teks || '';
        elPesan.classList.toggle('tersembunyi', !teks);
        elPesan.classList.toggle('kotak-gagal', !!gagal);
    }

    /* ---------------- filter ---------------- */

    function isiFilter() {
        var p = data.pilihan, m = data.meta;

        if (fTahun && !filterTerisi) {
            fTahun.innerHTML = (p.tahun || []).map(function (t) {
                return '<option value="' + t + '"'
                     + (String(t) === String(m.tahun) ? ' selected' : '') + '>' + t + '</option>';
            }).join('');
        }
        if (fBulan && !filterTerisi) {
            var h = '<option value="">Semua bulan</option>';
            for (var i = 0; i < 12; i++) {
                h += '<option value="' + (i + 1) + '">' + BULAN[i] + '</option>';
            }
            fBulan.innerHTML = h;
        }
        if (fLokasi) {
            var dipilih = fLokasi.value;
            fLokasi.innerHTML = '<option value="">Semua lokasi</option>'
                + (p.lokasi || []).map(function (x) {
                    return '<option value="' + x + '"'
                         + (x === dipilih ? ' selected' : '') + '>' + x + '</option>';
                }).join('');
        }
        filterTerisi = true;

        var waktu = document.getElementById('scanWaktu');
        if (waktu) waktu.textContent = m.waktuAmbil;
    }

    /* ---------------- gambar ---------------- */

    function gambar() {
        if (!data) return;

        if (elKpi) {
            elKpi.innerHTML = '';
            data.kpi.forEach(function (k, i) {
                var nilai = k.format === 'durasi' ? durasi(k.nilai) : G.angka(k.nilai);
                elKpi.insertAdjacentHTML('beforeend',
                    '<div class="kpi' + (k.utama ? ' kpi-utama' : '') + ' masuk" style="--tunda:'
                  + (i * 45) + 'ms">'
                  +   '<span class="kpi-judul">' + k.judul + '</span>'
                  +   '<span class="kpi-angka">' + nilai + '</span>'
                  + '</div>');
            });
        }

        var wJam = document.getElementById('chart-scan-jam');
        if (wJam) {
            G.batang(wJam, data.jam || [], {
                warna: '#1D9E75',
                format:     function (x) { return durasi(x.nilai); },
                keterangan: function (x) { return G.angka(x.struk) + ' struk'; },
                tip: function (x) {
                    return '<b>' + x.label + '</b><br>rata-rata ' + durasi(x.nilai)
                         + '<br>' + G.angka(x.struk) + ' struk';
                }
            });
        }

        var wKasir = document.getElementById('chart-scan-kasir');
        if (wKasir) {
            G.batang(wKasir, data.kasir || [], {
                warna: '#BA7517',
                format:     function (x) { return durasi(x.nilai); },
                keterangan: function (x) { return G.angka(x.struk) + ' struk'; },
                tip: function (x) {
                    return '<b>' + x.label + '</b><br>rata-rata ' + durasi(x.nilai)
                         + '<br>' + G.angka(x.struk) + ' struk'
                         + '<br>terlama ' + durasi(x.terlama);
                }
            });
        }

        tabel();
    }

    function tabel() {
        var t = document.getElementById('tabelScanLokasi');
        if (!t) return;
        var h = '<thead><tr><th>Lokasi</th><th class="ka">Struk</th>'
              + '<th class="ka">Rata-rata</th><th class="ka">Terlama</th>'
              + '<th class="ka">Pencilan</th></tr></thead><tbody>';
        (data.lokasi || []).forEach(function (x) {
            h += '<tr><td>' + x.label + '</td>'
               + '<td class="ka num">' + G.angka(x.struk) + '</td>'
               + '<td class="ka num">' + durasi(x.nilai) + '</td>'
               + '<td class="ka num">' + durasi(x.terlama) + '</td>'
               + '<td class="ka num">' + G.angka(x.pencilan) + '</td></tr>';
        });
        t.innerHTML = h + '</tbody>';
    }

    /* ---------------- bagian milik POS ---------------- */

    function aturMilikPos() {
        var disini = !panel.classList.contains('tersembunyi');
        milikPos.forEach(function (el) {
            if (el) el.classList.toggle('tersembunyi', disini);
        });
    }

    if (window.MutationObserver) {
        new MutationObserver(aturMilikPos)
            .observe(panel, { attributes: true, attributeFilter: ['class'] });
    }
    aturMilikPos();

    /* ---------------- pemicu ---------------- */

    /* Diambil saat tab dibuka pertama kali, bukan saat halaman dimuat. */
    tautan.addEventListener('click', function () {
        setTimeout(function () {
            aturMilikPos();
            if (!data && !sedangMuat) muat(false);
            else if (data) gambar();   // SVG yang digambar saat tersembunyi lebarnya nol
        }, 0);
    });

    Array.prototype.forEach.call(
        document.querySelectorAll('#tabBar a:not([data-tab="scantime"])'),
        function (a) {
            a.addEventListener('click', function () { setTimeout(aturMilikPos, 0); });
        });

    /* Kalau tab ini yang tersimpan terakhir kali, panelnya sudah terbuka saat
       halaman dimuat -- tidak ada klik yang menunggu. */
    if (!panel.classList.contains('tersembunyi')) {
        setTimeout(function () { muat(false); }, 300);
    }

    ['stTahun', 'stBulan', 'stLokasi'].forEach(function (id) {
        var el = document.getElementById(id);
        if (el) el.addEventListener('change', function () { muat(false); });
    });

    var tombol = document.getElementById('scanMuatUlang');
    if (tombol) tombol.addEventListener('click', function () { muat(true); });

    var jeda = null;
    window.addEventListener('resize', function () {
        clearTimeout(jeda);
        jeda = setTimeout(function () {
            if (data && !panel.classList.contains('tersembunyi')) gambar();
        }, 200);
    });
})();
