package com.sarinah.peoplecounter.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarinah.peoplecounter.entity.QuizBooth3Result;
import com.sarinah.peoplecounter.repository.QuizBooth3ResultRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/quiz-booth-3")
public class QuizBooth3Controller {

    private final QuizBooth3ResultRepository repository;
    private final ObjectMapper objectMapper;

    public QuizBooth3Controller(
            QuizBooth3ResultRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    private final Map<Integer, String> answerKey = Map.ofEntries(
            Map.entry(3, "B"),
            Map.entry(4, "A"),
            Map.entry(5, "B"),
            Map.entry(6, "C"),
            Map.entry(7, "A"),
            Map.entry(8, "B"),
            Map.entry(9, "A"),
            Map.entry(10, "D"),
            Map.entry(11, "D"),
            Map.entry(12, "A")
    );

    @GetMapping
    public QuizConfig getQuiz() {
        return new QuizConfig(
                "Interactive Quiz Booth 3",
                "Driving Connection & Innovation",
                100,
                List.of(
                        new Question(1, "Nama Lengkap", "text", List.of()),
                        new Question(2, "Divisi", "text", List.of()),

                        new Question(3, "Apa makna “Innovation”?", "choice", List.of(
                                new Option("A", "Mengikuti tren sementara"),
                                new Option("B", "Perubahan yang memberikan nilai & impact nyata"),
                                new Option("C", "Teknologi tanpa manusia"),
                                new Option("D", "Sekadar tampilan visual modern")
                        )),

                        new Question(4, "HC memiliki peran penting dalam…", "choice", List.of(
                                new Option("A", "Membangun budaya kolaboratif & employee engagement"),
                                new Option("B", "Mendesain UI aplikasi"),
                                new Option("C", "Menjual produk retail"),
                                new Option("D", "Mengelola database server")
                        )),

                        new Question(5, "Booth “Driving Connection & Innovation” paling menggambarkan…", "choice", List.of(
                                new Option("A", "Konsep retail"),
                                new Option("B", "Ruang kolaborasi"),
                                new Option("C", "Area formal perusahaan"),
                                new Option("D", "Ruang display biasa")
                        )),

                        new Question(6, "Apa peran utama IT dalam mendukung inovasi di Sarinah?", "choice", List.of(
                                new Option("A", "Mengatur campaign sosial media"),
                                new Option("B", "Membuat dekorasi event"),
                                new Option("C", "Mengembangkan solusi digital & integrasi sistem"),
                                new Option("D", "Mengelola merchandise")
                        )),

                        new Question(7, "Apa tujuan utama kolaborasi IT, Marketing, dan HC?", "choice", List.of(
                                new Option("A", "Menciptakan pengalaman yang connected & impactful"),
                                new Option("B", "Fokus pada internal saja"),
                                new Option("C", "Mengurangi interaksi karyawan"),
                                new Option("D", "Membuat divisi bekerja terpisah")
                        )),

                        new Question(8, "Mengapa booth ini menggunakan pendekatan digital interactive experience?", "choice", List.of(
                                new Option("A", "Menggantikan seluruh interaksi manusia"),
                                new Option("B", "Untuk menciptakan pengalaman yang lebih interaktif dan engaging"),
                                new Option("C", "Agar terlihat lebih besar"),
                                new Option("D", "Mengurangi komunikasi pengunjung")
                        )),

                        new Question(9, "“Driving Connection” paling erat kaitannya dengan…", "choice", List.of(
                                new Option("A", "Koneksi antara people, ideas, & technology"),
                                new Option("B", "Penjualan produk semata"),
                                new Option("C", "Sistem kerja individual"),
                                new Option("D", "Kompetisi antar divisi")
                        )),

                        new Question(10, "Mengapa komunikasi menjadi salah satu pilar utama dalam booth ini?", "choice", List.of(
                                new Option("A", "Agar booth terlihat ramai"),
                                new Option("B", "Untuk kebutuhan dekorasi"),
                                new Option("C", "Karena mengikuti tren desain"),
                                new Option("D", "Membantu membangun koneksi dan kolaborasi")
                        )),

                        new Question(11, "“Future Ready” dalam konteks booth ini berarti…", "choice", List.of(
                                new Option("A", "Mengandalkan sistem lama"),
                                new Option("B", "Mengikuti semua tren tanpa tujuan"),
                                new Option("C", "Fokus pada desain visual"),
                                new Option("D", "Siap menghadapi perubahan")
                        )),

                        new Question(12, "Interactive Digital Signage merepresentasikan…", "choice", List.of(
                                new Option("A", "Smart interactive technology experience"),
                                new Option("B", "Manual working system"),
                                new Option("C", "Area penyimpanan data fisik"),
                                new Option("D", "Traditional promotion")
                        ))
                )
        );
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody SubmitRequest request) throws JsonProcessingException {

        String cleanName = cleanText(request.name());
        String cleanDivision = cleanText(request.division());

        if (cleanName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Nama lengkap wajib diisi"
            ));
        }

        if (!isValidFullName(cleanName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Nama lengkap minimal 2 huruf dan hanya boleh huruf, spasi, titik, petik, atau strip"
            ));
        }

        if (repository.existsByNormalizedName(cleanName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Nama lengkap sudah pernah submit quiz. Tidak boleh duplikat."
            ));
        }

        if (cleanDivision.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Divisi wajib diisi"
            ));
        }

        Map<String, String> answers = request.answers() == null
                ? new HashMap<>()
                : request.answers();

        int score = 0;
        List<ResultItem> results = new ArrayList<>();

        for (int i = 3; i <= 12; i++) {
            String userAnswer = answers.getOrDefault("q" + i, "");
            String correctAnswer = answerKey.get(i);

            boolean correct = correctAnswer != null && correctAnswer.equalsIgnoreCase(userAnswer);

            if (correct) {
                score += 10;
            }

            results.add(new ResultItem(
                    i,
                    userAnswer,
                    correctAnswer,
                    correct,
                    correct ? 10 : 0
            ));
        }

        int durationSeconds = Math.max(request.durationSeconds(), 0);

        QuizBooth3Result entity = new QuizBooth3Result();
        entity.setName(cleanName);
        entity.setDivision(cleanDivision);
        entity.setScore(score);
        entity.setMaxScore(100);
        entity.setDurationSeconds(durationSeconds);
        entity.setAnswersJson(objectMapper.writeValueAsString(answers));
        entity.setResultJson(objectMapper.writeValueAsString(results));
        entity.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Jakarta")));

        QuizBooth3Result saved = repository.save(entity);

        return ResponseEntity.ok(new SubmitResponse(
                "SUCCESS",
                saved.getId(),
                score,
                100,
                durationSeconds,
                results
        ));
    }


    @GetMapping("/check-name")
    public ResponseEntity<?> checkName(@RequestParam(required = false) String name) {
        String cleanName = cleanText(name);

        if (cleanName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Nama lengkap wajib diisi"
            ));
        }

        if (!isValidFullName(cleanName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Nama lengkap minimal 2 huruf dan hanya boleh huruf, spasi, titik, petik, atau strip"
            ));
        }

        boolean duplicate = repository.existsByNormalizedName(cleanName);

        return ResponseEntity.ok(Map.of(
                "name", cleanName,
                "duplicate", duplicate,
                "message", duplicate
                        ? "Nama lengkap sudah pernah submit quiz."
                        : "Nama bisa digunakan."
        ));
    }

    // =========================================================
    // LEADERBOARD
    // URL:
    // GET /api/quiz-booth-3/leaderboard?limit=5
    //
    // Urutan:
    // 1. Score paling tinggi
    // 2. Duration paling cepat
    // 3. Created paling awal
    // =========================================================
    @GetMapping("/leaderboard")
    public ResponseEntity<?> leaderboard(
            @RequestParam(defaultValue = "5") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 50));

        var pageable = PageRequest.of(
                0,
                safeLimit,
                Sort.by(
                        Sort.Order.desc("score"),
                        Sort.Order.asc("durationSeconds"),
                        Sort.Order.asc("createdAt")
                )
        );

        List<QuizBooth3Result> rows = repository.findAll(pageable).getContent();

        List<LeaderboardItem> items = new ArrayList<>();

        int rank = 1;
        for (QuizBooth3Result row : rows) {
            items.add(new LeaderboardItem(
                    rank++,
                    row.getId(),
                    row.getName(),
                    row.getDivision(),
                    row.getScore(),
                    row.getMaxScore(),
                    row.getDurationSeconds(),
                    formatDuration(row.getDurationSeconds()),
                    row.getCreatedAt()
            ));
        }

        return ResponseEntity.ok(new LeaderboardResponse(
                "SUCCESS",
                items.size(),
                items
        ));
    }

    private String formatDuration(Integer seconds) {
        int total = seconds == null ? 0 : Math.max(seconds, 0);
        int minutes = total / 60;
        int remainingSeconds = total % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
    private String cleanText(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean isValidFullName(String value) {
        String clean = cleanText(value);

        if (clean.length() < 2 || clean.length() > 120) {
            return false;
        }

        // Boleh satu kata atau lebih.
        // Contoh valid:
        // Tanto
        // Sukarno
        // Aloysius Tanto Wibowo
        // Jean-Luc
        // O'Connor
        return clean.matches("^[\\p{L}][\\p{L} .'-]*$");
    }

    public record QuizConfig(
            String title,
            String subtitle,
            int maxScore,
            List<Question> questions
    ) {}

    public record Question(
            int number,
            String text,
            String type,
            List<Option> options
    ) {}

    public record Option(
            String value,
            String label
    ) {}

    public record SubmitRequest(
            String name,
            String division,
            int durationSeconds,
            Map<String, String> answers
    ) {}

    public record ResultItem(
            int questionNumber,
            String userAnswer,
            String correctAnswer,
            boolean correct,
            int points
    ) {}

    public record SubmitResponse(
            String status,
            Long resultId,
            int score,
            int maxScore,
            int durationSeconds,
            List<ResultItem> results
    ) {}

    public record LeaderboardResponse(
            String status,
            int total,
            List<LeaderboardItem> data
    ) {}

    public record LeaderboardItem(
            int rank,
            Long id,
            String name,
            String division,
            Integer score,
            Integer maxScore,
            Integer durationSeconds,
            String durationText,
            LocalDateTime createdAt
    ) {}
}