/* =========================================================================
   Pustaka grafik kecil berbasis SVG. Tanpa library luar, supaya tetap jalan
   di jaringan kantor yang tidak bisa mengakses internet.

   Dipakai oleh app.js lewat objek global `Grafik`.
   ========================================================================= */
(function () {
    var NS = 'http://www.w3.org/2000/svg';

    /* ---------------- pembantu ---------------- */

    function el(nama, atribut) {
        var n = document.createElementNS(NS, nama);
        for (var k in atribut) {
            if (atribut[k] !== null && atribut[k] !== undefined) n.setAttribute(k, atribut[k]);
        }
        return n;
    }

    function angka(v) {
        return Math.round(v).toLocaleString('id-ID');
    }

    function rupiah(v) {
        return 'Rp ' + angka(v);
    }

    /** Singkat jadi satuan yang enak dibaca: 1,2 M / 340 jt / 12 rb */
    function singkat(v) {
        var a = Math.abs(v);
        if (a >= 1e12) return desimal(v / 1e12, 1) + ' T';
        if (a >= 1e9)  return desimal(v / 1e9,  1) + ' M';
        if (a >= 1e6)  return desimal(v / 1e6,  0) + ' jt';
        if (a >= 1e3)  return desimal(v / 1e3,  0) + ' rb';
        return angka(v);
    }

    function desimal(v, n) {
        return v.toLocaleString('id-ID', { maximumFractionDigits: n });
    }

    /** Bulatkan batas atas sumbu ke angka yang rapi. */
    function batasRapi(maks) {
        if (maks <= 0) return 1;
        var pangkat = Math.pow(10, Math.floor(Math.log10(maks)));
        var sisa = maks / pangkat;
        var kali = sisa <= 1 ? 1 : sisa <= 2 ? 2 : sisa <= 2.5 ? 2.5 : sisa <= 5 ? 5 : 10;
        return kali * pangkat;
    }

    /* ---------------- tooltip bersama ---------------- */

    var tip = document.createElement('div');
    tip.className = 'tip';
    document.body.appendChild(tip);

    function tipPindah(ev) {
        var x = ev.clientX + 14, y = ev.clientY + 14;
        if (x + tip.offsetWidth  > window.innerWidth  - 8) x = ev.clientX - tip.offsetWidth  - 14;
        if (y + tip.offsetHeight > window.innerHeight - 8) y = ev.clientY - tip.offsetHeight - 14;
        tip.style.left = x + 'px';
        tip.style.top  = y + 'px';
    }

    function pasangTip(node, isi) {
        node.addEventListener('mouseenter', function (e) {
            tip.innerHTML = isi;
            tip.classList.add('aktif');
            tipPindah(e);
        });
        node.addEventListener('mousemove', tipPindah);
        node.addEventListener('mouseleave', function () { tip.classList.remove('aktif'); });
    }

    /* =====================================================================
       Kolom bertumpuk + garis total
       ===================================================================== */

    function kolomTren(wadah, data) {
        wadah.innerHTML = '';
        if (!data || !data.label || !data.label.length) {
            wadah.innerHTML = '<p class="kosong">Tidak ada data</p>';
            return;
        }

        var L = 76, R = 18, A = 26, B = 42;
        var W = Math.max(wadah.clientWidth || 0, 520);
        var H = 320;
        var pw = W - L - R, ph = H - A - B;

        var total = data.total || data.label.map(function (_, i) {
            return data.seri.reduce(function (s, x) { return s + (x.nilai[i] || 0); }, 0);
        });
        var maks = batasRapi(Math.max.apply(null, total.concat([0])));

        var svg = el('svg', { viewBox: '0 0 ' + W + ' ' + H, width: '100%', height: H,
                              role: 'img', 'aria-label': 'Tren nilai transaksi per bulan' });

        for (var g = 0; g <= 4; g++) {
            var y = A + ph - (ph * g / 4);
            svg.appendChild(el('line', { x1: L, y1: y, x2: L + pw, y2: y,
                                         stroke: 'var(--garis)', 'stroke-width': 1 }));
            var t = el('text', { x: L - 12, y: y + 4, 'text-anchor': 'end', class: 'sumbu' });
            t.textContent = g === 0 ? '0' : singkat(maks * g / 4);
            svg.appendChild(t);
        }

        var band = pw / data.label.length;
        var lebar = Math.min(band * 0.5, 44);
        var titik = [];

        data.label.forEach(function (nama, i) {
            var x = L + band * i + (band - lebar) / 2;
            var bawah = A + ph;

            data.seri.forEach(function (sr) {
                var v = sr.nilai[i] || 0;
                if (v <= 0) return;
                var tinggi = (v / maks) * ph;
                var yy = bawah - tinggi;
                var r = el('rect', { x: x, y: yy, width: lebar,
                                     height: Math.max(tinggi - 2, 1),
                                     fill: sr.warna, rx: 3, opacity: 0.9 });
                pasangTip(r, '<b>' + nama + '</b><br>' + sr.nama + '<br>' + rupiah(v));
                svg.appendChild(r);
                bawah = yy;
            });

            titik.push({ x: L + band * i + band / 2,
                         y: A + ph - (total[i] / maks) * ph,
                         nama: nama, nilai: total[i] });

            var lb = el('text', { x: L + band * i + band / 2, y: H - 16,
                                  'text-anchor': 'middle', class: 'sumbu' });
            lb.textContent = nama.substring(0, 3);
            svg.appendChild(lb);
        });

        /* garis total */
        if (titik.length > 1) {
            var d = titik.map(function (p, i) {
                return (i === 0 ? 'M' : 'L') + p.x + ' ' + p.y;
            }).join(' ');
            svg.appendChild(el('path', { d: d, fill: 'none', stroke: '#2563EB',
                                         'stroke-width': 2, 'stroke-linejoin': 'round' }));
        }
        titik.forEach(function (p) {
            var c = el('circle', { cx: p.x, cy: p.y, r: 4, fill: '#fff',
                                   stroke: '#2563EB', 'stroke-width': 2 });
            pasangTip(c, '<b>' + p.nama + '</b><br>Total ' + rupiah(p.nilai));
            svg.appendChild(c);
        });

        svg.appendChild(el('line', { x1: L, y1: A + ph, x2: L + pw, y2: A + ph,
                                     stroke: 'var(--garis-kuat)', 'stroke-width': 1 }));
        wadah.appendChild(svg);

        var lg = document.createElement('div');
        lg.className = 'legenda';
        data.seri.forEach(function (sr) {
            lg.insertAdjacentHTML('beforeend',
                '<span class="lg"><i class="kotak" style="background:' + sr.warna + '"></i>'
              + '<span>' + sr.nama + '</span></span>');
        });
        wadah.appendChild(lg);
    }

    /* =====================================================================
       Batang mendatar
       ===================================================================== */

    function batang(wadah, item, opsi) {
        opsi = opsi || {};
        if (!item || !item.length) {
            wadah.innerHTML = '<p class="kosong">Tidak ada data</p>';
            return;
        }
        var warna = opsi.warna || '#2563EB';
        var maks = Math.max.apply(null, item.map(function (x) { return Math.abs(x.nilai); })) || 1;

        var html = '';
        item.forEach(function (x, i) {
            var p = Math.max(Math.abs(x.nilai) / maks * 100, 0.6);
            var utama = opsi.format ? opsi.format(x) : singkat(x.nilai);
            var bawah = opsi.keterangan ? opsi.keterangan(x) : '';
            html += '<div class="bc-item" style="--tunda:' + (i * 35) + 'ms">'
                  +   '<span class="bc-label" title="' + x.label + '">' + x.label + '</span>'
                  +   '<div class="bc-track"><div class="bc-bar" style="width:' + p + '%;background:'
                  +      warna + '" data-tip="' + (opsi.tip ? opsi.tip(x) : rupiah(x.nilai)) + '"></div></div>'
                  +   '<span class="bc-nilai">' + utama
                  +     (bawah ? '<em>' + bawah + '</em>' : '') + '</span>'
                  + '</div>';
        });
        wadah.innerHTML = html;
        wadah.querySelectorAll('.bc-bar').forEach(function (b) {
            pasangTip(b, b.getAttribute('data-tip'));
        });
    }

    /* =====================================================================
       Donat + daftar nilai
       ===================================================================== */

    function donut(wadah, item, judulTengah) {
        wadah.innerHTML = '';
        if (!item || !item.length) {
            wadah.innerHTML = '<p class="kosong">Tidak ada data</p>';
            return;
        }
        var total = item.reduce(function (s, x) { return s + Math.abs(x.nilai); }, 0) || 1;

        var U = 180, R = 74, r = 50, C = U / 2;
        var svg = el('svg', { viewBox: '0 0 ' + U + ' ' + U, width: U, height: U,
                              role: 'img', 'aria-label': 'Komposisi' });

        var mulai = -Math.PI / 2;
        item.forEach(function (x) {
            var porsi = Math.abs(x.nilai) / total;
            var akhir = mulai + porsi * Math.PI * 2;
            var besar = porsi > 0.5 ? 1 : 0;

            var x1 = C + R * Math.cos(mulai), y1 = C + R * Math.sin(mulai);
            var x2 = C + R * Math.cos(akhir), y2 = C + R * Math.sin(akhir);
            var x3 = C + r * Math.cos(akhir), y3 = C + r * Math.sin(akhir);
            var x4 = C + r * Math.cos(mulai), y4 = C + r * Math.sin(mulai);

            var d = 'M' + x1 + ' ' + y1
                  + 'A' + R + ' ' + R + ' 0 ' + besar + ' 1 ' + x2 + ' ' + y2
                  + 'L' + x3 + ' ' + y3
                  + 'A' + r + ' ' + r + ' 0 ' + besar + ' 0 ' + x4 + ' ' + y4 + 'Z';

            var p = el('path', { d: d, fill: x.warna, stroke: 'var(--panel)', 'stroke-width': 2 });
            pasangTip(p, '<b>' + x.label + '</b><br>' + rupiah(x.nilai)
                       + '<br>' + desimal(porsi * 100, 1) + '%');
            svg.appendChild(p);
            mulai = akhir;
        });

        var tengah = el('text', { x: C, y: C - 4, 'text-anchor': 'middle', class: 'donut-angka' });
        tengah.textContent = singkat(total);
        svg.appendChild(tengah);
        var sub = el('text', { x: C, y: C + 14, 'text-anchor': 'middle', class: 'sumbu' });
        sub.textContent = judulTengah || 'Total';
        svg.appendChild(sub);

        var bungkus = document.createElement('div');
        bungkus.className = 'donut-bungkus';
        bungkus.appendChild(svg);

        var daftar = document.createElement('div');
        daftar.className = 'donut-daftar';
        item.forEach(function (x) {
            var porsi = Math.abs(x.nilai) / total * 100;
            daftar.insertAdjacentHTML('beforeend',
                '<div class="dl-item"><i class="kotak" style="background:' + x.warna + '"></i>'
              + '<span class="dl-label">' + x.label + '</span>'
              + '<b>' + desimal(porsi, 1) + '%</b></div>');
        });
        bungkus.appendChild(daftar);
        wadah.appendChild(bungkus);
    }

    /* =====================================================================
       Sparkline untuk kartu KPI
       ===================================================================== */

    function sparkline(deret, warna) {
        if (!deret || deret.length < 2) return '';
        var W = 88, H = 28;
        var maks = Math.max.apply(null, deret);
        var min  = Math.min.apply(null, deret);
        var rentang = (maks - min) || 1;
        var lebar = W / deret.length;

        var bar = '';
        deret.forEach(function (v, i) {
            var t = Math.max(((v - min) / rentang) * (H - 4), 2);
            bar += '<rect x="' + (i * lebar) + '" y="' + (H - t) + '" width="'
                 + (lebar - 2) + '" height="' + t + '" rx="1.5" fill="' + warna
                 + '" opacity="' + (0.35 + 0.65 * (i + 1) / deret.length) + '"/>';
        });
        return '<svg class="spark" viewBox="0 0 ' + W + ' ' + H + '" width="' + W
             + '" height="' + H + '" aria-hidden="true">' + bar + '</svg>';
    }

    window.Grafik = {
        kolomTren: kolomTren,
        batang: batang,
        donut: donut,
        sparkline: sparkline,
        angka: angka,
        rupiah: rupiah,
        singkat: singkat,
        desimal: desimal
    };
})();
