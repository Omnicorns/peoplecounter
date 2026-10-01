/* =========================================================================
   Pengendali halaman: mengambil data dari /api/dashboard, menampilkan efek
   loading selama menunggu, lalu mengisi seluruh isi dashboard.
   ========================================================================= */
(function () {
    var G = window.Grafik;
    var data = null;          // isi terakhir dari API

    /* ---------------- elemen yang sering dipakai ---------------- */
    var tirai      = document.getElementById('tirai');
    var tiraiLang  = document.getElementById('tiraiLangkah');
    var tiraiWaktu = document.getElementById('tiraiWaktu');
    var progres    = document.getElementById('progresIsi');
    var tahapLi    = Array.prototype.slice.call(document.querySelectorAll('#tahapDaftar li'));
    var tiraiTips  = document.getElementById('tiraiTips');

    var LANGKAH = [
        'Menghubungi database...',
        'Membaca data pembayaran...',
        'Menghitung alokasi brand...',
        'Menyusun grafik...'
    ];

    /* =====================================================================
       Efek loading
       ===================================================================== */

    var jamTirai = null, mulaiTirai = 0, tahapKini = 0;

    function bukaTirai() {
        tirai.classList.add('aktif');
        tiraiTips.classList.add('tersembunyi');
        mulaiTirai = Date.now();
        tahapKini = 0;
        setTahap(0);
        progres.style.width = '6%';

        jamTirai = setInterval(function () {
            var detik = Math.floor((Date.now() - mulaiTirai) / 1000);
            tiraiWaktu.textContent = detik + ' detik';

            /* Tahapan maju mengikuti waktu: 0-2 dtk, 2-15 dtk, 15-45 dtk, lalu terakhir. */
            var t = detik < 2 ? 0 : detik < 15 ? 1 : detik < 45 ? 2 : 3;
            if (t !== tahapKini) { tahapKini = t; setTahap(t); }

            /* Bar merayap mendekati 92% tanpa pernah sampai, lalu dituntaskan. */
            var p = Math.min(92, 6 + detik * 2.2);
            progres.style.width = p + '%';

            /* Menunggu lama berarti tabel ringkasan belum dibuat. */
            if (detik >= 60) tiraiTips.classList.remove('tersembunyi');
        }, 250);
    }

    function setTahap(i) {
        tiraiLang.textContent = LANGKAH[i];
        tahapLi.forEach(function (li) {
            var n = parseInt(li.getAttribute('data-tahap'), 10);
            li.classList.toggle('selesai', n < i);
            li.classList.toggle('jalan',   n === i);
        });
    }

    function tutupTirai() {
        if (jamTirai) { clearInterval(jamTirai); jamTirai = null; }
        progres.style.width = '100%';
        setTahap(3);
        tahapLi.forEach(function (li) {
            li.classList.add('selesai');
            li.classList.remove('jalan');
        });
        setTimeout(function () { tirai.classList.remove('aktif'); }, 320);
    }

    /* Rangka abu-abu berdenyut selama isi belum datang. */
    function pasangSkeleton() {
        var kpi = document.getElementById('kpiBaris');
        kpi.innerHTML = '';
        for (var i = 0; i < 6; i++) {
            kpi.insertAdjacentHTML('beforeend',
                '<div class="kpi rangka-muat"><span class="sk sk-judul"></span>'
              + '<span class="sk sk-angka"></span><span class="sk sk-kecil"></span></div>');
        }
        document.querySelectorAll('.bar-chart').forEach(function (w) {
            var h = '';
            for (var i = 0; i < 7; i++) {
                h += '<div class="bc-item"><span class="sk sk-label"></span>'
                   + '<span class="sk sk-bar"></span><span class="sk sk-kecil"></span></div>';
            }
            w.innerHTML = h;
        });
        document.querySelectorAll('.wadah-chart, .wadah-donut').forEach(function (w) {
            w.innerHTML = '<div class="sk sk-blok"></div>';
        });
    }

    /* =====================================================================
       Pengambilan data
       ===================================================================== */

    function paramSekarang(tambahan) {
        var p = new URLSearchParams();
        ['tahun', 'bulan', 'bank', 'branch', 'lokasi', 'metode'].forEach(function (k) {
            var sel = document.getElementById('f' + k.charAt(0).toUpperCase() + k.slice(1));
            if (sel && sel.value) p.set(k, sel.value);
        });
        var kartu = document.getElementById('fKartu');
        if (kartu && kartu.checked) p.set('hanyaKartu', 'true');
        if (tambahan) for (var k in tambahan) p.set(k, tambahan[k]);
        return p;
    }

    function ambil(tambahan) {
        bukaTirai();
        pasangSkeleton();

        var p = paramSekarang(tambahan);
        fetch('/api/dashboard?' + p.toString(), { headers: { 'Accept': 'application/json' } })
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (j) {
                if (j.error) { tampilkanGagal(j.error, j.rinci); tutupTirai(); return; }
                data = j;
                sembunyikanGagal();
                gambarSemua();
                tutupTirai();
            })
            .catch(function (e) {
                tampilkanGagal('Tidak bisa menghubungi server aplikasi.', e.message);
                tutupTirai();
            });
    }

    function tampilkanGagal(pesan, rinci) {
        var kotak = document.getElementById('kotakGagal');
        document.getElementById('gagalPesan').textContent = pesan || '';
        document.getElementById('gagalRinci').textContent = rinci || '';
        kotak.classList.remove('tersembunyi');
        document.getElementById('kpiBaris').innerHTML = '';
        document.querySelectorAll('.bar-chart, .wadah-chart, .wadah-donut')
                .forEach(function (w) { w.innerHTML = ''; });
    }

    function sembunyikanGagal() {
        document.getElementById('kotakGagal').classList.add('tersembunyi');
    }

    /* =====================================================================
       Filter
       ===================================================================== */

    function isiFilter() {
        var p = data.pilihan, m = data.meta;

        isiSelect('fTahun',  p.tahun.map(String), String(m.tahun), null);
        isiSelect('fBulan',  p.bulan, m.bulan ? String(m.bulan) : '', 'Semua bulan', true);
        isiSelect('fBank',   p.bank,   nilaiSel('fBank'),   'Semua bank');
        isiSelect('fBranch', p.branch, nilaiSel('fBranch'), 'Semua cabang');
        isiSelect('fLokasi', p.lokasi, nilaiSel('fLokasi'), 'Semua lokasi');
        isiSelect('fMetode', p.metode, nilaiSel('fMetode'), 'Semua metode');
    }

    var nilaiTersimpan = {};

    function nilaiSel(id) {
        return nilaiTersimpan[id] || '';
    }

    function isiSelect(id, daftar, terpilih, kosong, pakaiIndex) {
        var sel = document.getElementById(id);
        if (!sel) return;
        var h = kosong ? '<option value="">' + kosong + '</option>' : '';
        daftar.forEach(function (v, i) {
            var val = pakaiIndex ? String(i + 1) : v;
            h += '<option value="' + val + '"' + (val === terpilih ? ' selected' : '')
               + '>' + v + '</option>';
        });
        sel.innerHTML = h;
        nilaiTersimpan[id] = sel.value;
    }

    document.getElementById('formFilter').addEventListener('change', function (ev) {
        var t = ev.target;
        if (t.id) nilaiTersimpan[t.id] = t.type === 'checkbox' ? '' : t.value;
        ambil();
    });

    document.getElementById('tombolReset').addEventListener('click', function () {
        ['fBulan', 'fBank', 'fBranch', 'fLokasi', 'fMetode'].forEach(function (id) {
            var s = document.getElementById(id);
            if (s) { s.value = ''; nilaiTersimpan[id] = ''; }
        });
        document.getElementById('fKartu').checked = false;
        ambil();
    });

    document.getElementById('tombolMuatUlang').addEventListener('click', function () {
        ambil({ muatUlang: 'true' });
    });

    /* =====================================================================
       Menggambar isi
       ===================================================================== */

    function gambarSemua() {
        if (!data) return;
        isiFilter();
        isiKepala();
        isiKpi();
        isiTren();
        isiBatang();
        isiDonut();
        isiTabel();
        isiSorot();
        isiInsight();
        isiPivot();
        isiUnduhan();
    }

    function isiKepala() {
        var m = data.meta;
        document.getElementById('judulTahun').textContent = m.tahun;
        document.getElementById('waktuAmbil').textContent = m.waktuAmbil;
        document.getElementById('statusTitik').classList.toggle('merah', !m.brandSiap);

        var sumber = document.getElementById('sumberData');
        if (m.sumberPayment === 'tabel ringkasan' && m.sumberBrand === 'tabel ringkasan') {
            sumber.textContent = 'Sumber: tabel ringkasan';
            sumber.className = 'sumber-cepat';
        } else {
            sumber.textContent = 'Sumber: hitung langsung (' + m.detik + ' detik)';
            sumber.className = 'sumber-lambat';
        }

        var kini = new Date();
        var hari = ['Minggu','Senin','Selasa','Rabu','Kamis','Jumat','Sabtu'][kini.getDay()];
        var bln  = ['Jan','Feb','Mar','Apr','Mei','Jun','Jul','Agu','Sep','Okt','Nov','Des'][kini.getMonth()];
        document.getElementById('tanggalHariIni').textContent =
            hari + ', ' + kini.getDate() + ' ' + bln + ' ' + kini.getFullYear();
        document.getElementById('jamHariIni').textContent =
            kini.toTimeString().slice(0, 8) + ' WIB';
    }

    function isiKpi() {
        var wadah = document.getElementById('kpiBaris');
        wadah.innerHTML = '';
        data.kpi.forEach(function (k, i) {
            var nilai = k.format === 'rupiah' ? G.rupiah(k.nilai) : G.angka(k.nilai);
            var warna = k.utama ? '#ffffff' : '#2563EB';

            var delta = '';
            if (k.delta !== null && k.delta !== undefined) {
                var naik = k.delta >= 0;
                delta = '<span class="kpi-delta ' + (naik ? 'naik' : 'turun') + '">'
                      + (naik ? '▲' : '▼') + ' ' + G.desimal(Math.abs(k.delta), 1) + '%</span>'
                      + '<span class="kpi-dasar">' + (k.dasar || '') + '</span>';
            }

            wadah.insertAdjacentHTML('beforeend',
                '<div class="kpi' + (k.utama ? ' kpi-utama' : '') + ' masuk" style="--tunda:'
              + (i * 45) + 'ms">'
              +   '<span class="kpi-judul">' + k.judul + '</span>'
              +   '<span class="kpi-angka">' + nilai + '</span>'
              +   '<div class="kpi-kaki">' + delta + G.sparkline(k.spark, warna) + '</div>'
              + '</div>');
        });
    }

    function isiTren() {
        G.kolomTren(document.getElementById('chart-bulan'), data.tren);

        var total = (data.tren.total || []).reduce(function (a, b) { return a + b; }, 0);
        document.getElementById('trenTotal').textContent = G.rupiah(total);

        var kpi = data.kpi[0];
        var el = document.getElementById('trenDelta');
        if (kpi && kpi.delta !== null && kpi.delta !== undefined) {
            var naik = kpi.delta >= 0;
            el.className = 'delta ' + (naik ? 'naik' : 'turun');
            el.textContent = (naik ? '▲ ' : '▼ ') + G.desimal(Math.abs(kpi.delta), 1)
                           + '%  ' + (kpi.dasar || '');
        } else {
            el.textContent = '';
        }
    }

    function isiBatang() {
        var trxOpsi = function (warna) {
            return {
                warna: warna,
                format: function (x) { return G.angka(x.struk); },
                keterangan: function () { return 'transaksi'; },
                tip: function (x) {
                    return '<b>' + x.label + '</b><br>' + G.angka(x.struk) + ' transaksi<br>'
                         + G.rupiah(x.rupiah) + '<br>basket ' + G.rupiah(x.basket);
                }
            };
        };
        var nilaiOpsi = function (warna) {
            return {
                warna: warna,
                keterangan: function (x) { return G.angka(x.struk) + ' struk'; },
                tip: function (x) {
                    return '<b>' + x.label + '</b><br>' + G.rupiah(x.nilai) + '<br>'
                         + G.angka(x.struk) + ' struk<br>basket ' + G.rupiah(x.basket);
                }
            };
        };

        gambar('chart-grup',       data.grup,      nilaiOpsi('#2563EB'));
        gambar('chart-grup2',      data.grup,      nilaiOpsi('#2563EB'));
        gambar('chart-brandtop',   data.brandTop,  nilaiOpsi('#1D9E75'));
        gambar('chart-brandtop2',  data.brandTop,  nilaiOpsi('#1D9E75'));
        gambar('chart-bank',       data.bank,      nilaiOpsi('#2563EB'));
        gambar('chart-lokasi',     data.lokasi,    nilaiOpsi('#2563EB'));
        gambar('chart-lantai',     data.lantai,    nilaiOpsi('#D4537E'));
        gambar('chart-branch-trx', data.branchtrx, trxOpsi('#1D9E75'));
        gambar('chart-lantai-trx', data.lantaitrx, trxOpsi('#BA7517'));
        gambar('chart-kelas',      data.kelas, {
            warna: '#7F77DD',
            format: function (x) { return G.rupiah(x.basket); },
            keterangan: function (x) { return G.angka(x.struk) + ' struk'; },
            tip: function (x) {
                return '<b>' + x.label + '</b><br>basket size ' + G.rupiah(x.basket)
                     + '<br>' + G.angka(x.struk) + ' struk';
            }
        });
    }

    function gambar(id, isi, opsi) {
        var w = document.getElementById(id);
        if (!w) return;
        if (!isi) { w.innerHTML = '<p class="kosong">Data tidak tersedia</p>'; return; }
        G.batang(w, isi, opsi);
    }

    function isiDonut() {
        G.donut(document.getElementById('chart-metode'), data.metode, 'Total Transaksi');
        if (data.kategori) {
            G.donut(document.getElementById('chart-kategori'),  data.kategori, 'Total Transaksi');
            G.donut(document.getElementById('chart-kategori2'), data.kategori, 'Total Transaksi');
        } else {
            ['chart-kategori', 'chart-kategori2'].forEach(function (id) {
                document.getElementById(id).innerHTML =
                    '<p class="kosong">Data brand tidak tersedia</p>';
            });
        }
    }

    function isiTabel() {
        var t = document.getElementById('tabelLokasi');
        var h = '<thead><tr><th>#</th><th>Lokasi</th><th class="ka">Nilai</th>'
              + '<th class="ka">Struk</th><th class="ka">Porsi</th></tr></thead><tbody>';
        (data.tabelLokasi || []).forEach(function (x, i) {
            h += '<tr><td class="urut">' + (i + 1) + '</td><td>' + x.label + '</td>'
               + '<td class="ka num">' + G.singkat(x.nilai) + '</td>'
               + '<td class="ka num">' + G.angka(x.struk) + '</td>'
               + '<td class="ka num">' + G.desimal(x.persen, 1) + '%</td></tr>';
        });
        t.innerHTML = h + '</tbody>';

        var tb = document.getElementById('tabelBankBrand');
        if (!data.bankBrand) {
            tb.innerHTML = '<tbody><tr><td class="kosong">Data brand tidak tersedia</td></tr></tbody>';
            return;
        }
        var hb = '<thead><tr><th>#</th><th>Bank Issuer</th><th>Grup Brand</th>'
               + '<th class="ka">Nilai</th><th class="ka">Struk</th>'
               + '<th class="ka">Kontribusi</th></tr></thead><tbody>';
        data.bankBrand.forEach(function (x, i) {
            hb += '<tr><td class="urut">' + (i + 1) + '</td><td>' + x.bank + '</td>'
                + '<td>' + x.brand + '</td>'
                + '<td class="ka num">' + G.singkat(x.nilai) + '</td>'
                + '<td class="ka num">' + G.angka(x.struk) + '</td>'
                + '<td class="ka num">' + G.desimal(x.kontribusi, 1) + '%</td></tr>';
        });
        tb.innerHTML = hb + '</tbody>';
    }

    function isiSorot() {
        var wadah = document.getElementById('sorotBaris');
        var p = data.puncak || {};
        var judul = { lokasi: 'Lokasi paling ramai', branch: 'Branch paling ramai',
                      lantai: 'Lantai paling ramai' };
        var h = '';
        ['lokasi', 'branch', 'lantai'].forEach(function (k) {
            var x = p[k];
            if (!x) return;
            h += '<div class="sorot"><span class="sorot-judul">' + judul[k] + '</span>'
               + '<span class="sorot-nama">' + x.label + '</span>'
               + '<div class="sorot-angka"><span><b>' + G.angka(x.struk)
               + '</b> transaksi</span><span>' + G.rupiah(x.nilai) + '</span></div></div>';
        });
        wadah.innerHTML = h;
    }

    function isiInsight() {
        var wadah = document.getElementById('insightBaris');
        var ikon = { naik: '▲', turun: '▼', bintang: '★', lokasi: '◉', lantai: '▦', kartu: '▣' };
        var h = '';
        (data.insight || []).forEach(function (x, i) {
            h += '<div class="insight masuk" style="--tunda:' + (i * 60) + 'ms">'
               +   '<span class="insight-ikon">' + (ikon[x.ikon] || '•') + '</span>'
               +   '<div><span class="insight-judul">' + x.judul + '</span>'
               +   '<span class="insight-sorot">' + x.sorot + '</span>'
               +   '<p>' + x.teks + '</p></div>'
               + '</div>';
        });
        wadah.innerHTML = h || '<p class="kosong">Belum ada insight untuk filter ini</p>';
    }

    function isiPivot() {
        var w = document.getElementById('wadahPivot');
        var p = data.pivot;
        if (!p || !p.baris || !p.baris.length) {
            w.innerHTML = '<p class="kosong">Tidak ada data untuk filter ini</p>';
            return;
        }
        var h = '<table class="pivot"><thead><tr>'
              + '<th class="beku beku-1" rowspan="2">Bank Issuer</th>'
              + '<th class="beku beku-2" rowspan="2">Payment Method</th>';
        p.grupBulan.forEach(function (g) {
            h += '<th class="ka grup" colspan="' + g.jumlahKolom + '">' + g.bulan + '</th>';
        });
        h += '<th class="ka total-kol" rowspan="2">Total</th></tr><tr>';
        p.kolom.forEach(function (k) { h += '<th class="ka">' + k.cardType + '</th>'; });
        h += '</tr></thead><tbody>';

        p.baris.forEach(function (b) {
            h += '<tr><td class="beku beku-1">' + b.bank + '</td>'
               + '<td class="beku beku-2">' + b.metode + '</td>';
            b.sel.forEach(function (v) {
                var n = Number(v);
                h += '<td class="ka num' + (n < 0 ? ' minus' : '') + '">'
                   + (n === 0 ? '' : G.angka(n)) + '</td>';
            });
            h += '<td class="ka num tebal total-kol">' + G.angka(Number(b.total)) + '</td></tr>';
        });

        h += '</tbody><tfoot><tr><td class="beku beku-1 tebal">Grand Total</td>'
           + '<td class="beku beku-2"></td>';
        p.total.sel.forEach(function (v) {
            var n = Number(v);
            h += '<td class="ka num tebal">' + (n === 0 ? '' : G.angka(n)) + '</td>';
        });
        h += '<td class="ka num tebal total-kol">' + G.angka(Number(p.total.total))
           + '</td></tr></tfoot></table>';
        w.innerHTML = h;
    }

    function isiUnduhan() {
        var p = paramSekarang();
        p.delete('bulan');
        document.getElementById('unduhPivot').href = '/pivot.csv?' + p.toString();
        document.getElementById('unduhData').href  = '/data.csv?tahun=' + data.meta.tahun;

        var b = paramSekarang();
        b.delete('lokasi');
        b.delete('metode');
        document.getElementById('unduhBrand').href = '/brand.csv?' + b.toString();
    }

    /* =====================================================================
       Tab
       ===================================================================== */

    (function () {
        var bar = document.getElementById('tabBar');
        var KUNCI = 'sarinah.dashboard.tab';
        var tautan = Array.prototype.slice.call(bar.querySelectorAll('a[data-tab]'));
        var panel  = Array.prototype.slice.call(document.querySelectorAll('.tab[data-panel]'));

        function pilih(nama, simpan) {
            if (!tautan.some(function (a) { return a.getAttribute('data-tab') === nama; })) {
                nama = 'ringkasan';
            }
            tautan.forEach(function (a) {
                a.classList.toggle('aktif', a.getAttribute('data-tab') === nama);
            });
            panel.forEach(function (p) {
                p.classList.toggle('tersembunyi', p.getAttribute('data-panel') !== nama);
            });
            if (simpan) { try { localStorage.setItem(KUNCI, nama); } catch (e) {} }
            /* grafik SVG yang digambar saat tersembunyi punya lebar nol */
            if (data) { isiTren(); isiDonut(); }
        }

        tautan.forEach(function (a) {
            a.addEventListener('click', function (ev) {
                ev.preventDefault();
                pilih(a.getAttribute('data-tab'), true);
            });
        });

        var awal = 'ringkasan';
        try { awal = localStorage.getItem(KUNCI) || 'ringkasan'; } catch (e) {}
        pilih(awal, false);
    })();

    /* gambar ulang saat lebar jendela berubah */
    var jeda = null;
    window.addEventListener('resize', function () {
        clearTimeout(jeda);
        jeda = setTimeout(function () { if (data) { isiTren(); isiDonut(); } }, 200);
    });

    /* ---------------- mulai ---------------- */
    ambil();

     function ambilDiam() {
            fetch('/api/dashboard?' + paramSekarang().toString(),
                  { headers: { 'Accept': 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (j) {
                    if (j && !j.error) { data = j; gambarSemua(); }
                })
                .catch(function () { /* diam saja */ });
        }

    setInterval(ambilDiam, 10 * 60 * 1000);
})();
