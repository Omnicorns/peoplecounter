package com.sarinah.peoplecounter.controller;

import com.sarinah.peoplecounter.entity.SopDocument;
import com.sarinah.peoplecounter.entity.SopFile;
import com.sarinah.peoplecounter.repository.SopDocumentRepository;
import com.sarinah.peoplecounter.repository.SopFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;


import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sop")
@RequiredArgsConstructor
public class SopController {
    private final SopDocumentRepository docs;
    private final SopFileRepository files;

    @GetMapping
    public Map<String,Object> list(@RequestParam(required=false) String query,
                                   @RequestParam(required=false) String status){

        // helper
        String q = (query == null || query.isBlank()) ? null : "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
        String st = (status == null || status.isBlank()) ? null : status.toLowerCase(java.util.Locale.ROOT);

        var items = docs.search(q, st)   // << gunakan qLike & statusLower
                .stream()
                .map(d -> {
                    var fs = files.findBySopId(d.getId()).stream()
                            .map(f -> {
                                var mf = new LinkedHashMap<String, Object>();
                                mf.put("id", f.getId());
                                mf.put("fileName", f.getFileName());
                                mf.put("mime", f.getMime());
                                mf.put("size", f.getSize());
                                return mf;
                            }).toList();

                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", d.getId());
                    m.put("code", d.getCode());
                    m.put("title", d.getTitle());
                    m.put("version", d.getVersion());
                    m.put("status", d.getStatus());
                    m.put("category", d.getCategory());
                    m.put("owner", d.getOwner());
                    m.put("effective", d.getEffective());
                    m.put("tags", d.getTags());
                    m.put("change", d.getChangeSummary());
                    m.put("files", fs);
                    return m;
                })
                .toList();

        return Map.of("items", items);
    }


    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable Long id){
        var d = docs.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "File dengan id " + id+ " tidak ditemukan"));;

        var fs = files.findBySopId(id).stream()
                .map(f -> {
                    var mf = new LinkedHashMap<String, Object>();
                    mf.put("id", f.getId());
                    mf.put("fileName", f.getFileName());
                    mf.put("mime", f.getMime());
                    mf.put("size", f.getSize());
                    return mf;
                }).toList();

        var m = new LinkedHashMap<String, Object>();
        m.put("id", d.getId());
        m.put("code", d.getCode());
        m.put("title", d.getTitle());
        m.put("version", d.getVersion());
        m.put("status", d.getStatus());
        m.put("category", d.getCategory());
        m.put("owner", d.getOwner());
        m.put("effective", d.getEffective());
        m.put("tags", d.getTags());
        m.put("change", d.getChangeSummary());
        m.put("files", fs);
        return m;
    }

    /* ========= CREATE ========= */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> create(@RequestParam String code,
                                     @RequestParam String title,
                                     @RequestParam(required=false) String category,
                                     @RequestParam(required=false) String status,
                                     @RequestParam(required=false) String owner,
                                     @RequestParam(required=false) String effective,
                                     @RequestParam(required=false) String tags,
                                     @RequestParam(required=false, name="change") String change,
                                     @RequestParam(required=false, name="files") MultipartFile[] uploads) throws Exception {
        if(docs.findByCode(code).isPresent()) throw new IllegalArgumentException("code exists");

        var d = new SopDocument();
        d.setCode(code); d.setTitle(title);
        d.setCategory(category); d.setStatus(status);
        d.setOwner(owner);
        d.setEffective(parseDateOrNull(effective));
        d.setChangeSummary(change);
        d.setTags(parseTags(tags));
        docs.save(d);

        saveFilesToDb(d, uploads);
        return Map.of("id", d.getId(), "code", d.getCode());
    }


    /* ========= UPDATE ========= */
    @PutMapping(value="/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> update(@PathVariable Long id,
                                     @RequestParam(required=false) String code,
                                     @RequestParam(required=false) String title,
                                     @RequestParam(required=false) String category,
                                     @RequestParam(required=false) String status,
                                     @RequestParam(required=false) String owner,
                                     @RequestParam(required=false) String effective,
                                     @RequestParam(required=false) String tags,
                                     @RequestParam(required =false) String version,
                                     @RequestParam(required=false, name="change") String change,
                                     @RequestParam(required=false, name="files") MultipartFile[] uploads) throws Exception {
        var d = docs.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "File dengan id " + id + " tidak ditemukan"));

        if(code != null && !code.isBlank() && !code.equals(d.getCode())){
            if(docs.findByCode(code).isPresent()) throw new IllegalArgumentException("code exists");
            d.setCode(code);
        }
        if(title != null) d.setTitle(title);
        if(category != null) d.setCategory(category);
        if(status != null) d.setStatus(status);
        if(owner != null) d.setOwner(owner);
        if(effective != null && !effective.isBlank()) d.setEffective(LocalDate.parse(effective));
        if(change != null) d.setChangeSummary(change);
        if(tags != null) d.setTags(parseTags(tags));
        if(version != null) d.setVersion(version);

        docs.save(d);
        saveFilesToDb(d, uploads); // menambah file baru; yang lama tetap ada
        return Map.of("id", d.getId(), "code", d.getCode());
    }

    /* ========= DELETE ========= */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var d = docs.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "File dengan id " + id + " tidak ditemukan"));

        files.deleteAll(files.findBySopId(id));
        docs.delete(d);
        return ResponseEntity.noContent().build();
    }

    /* ========= DOWNLOAD ========= */
    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
        var f = files.findById(fileId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "File dengan id " + fileId + " tidak ditemukan"));

        if(!Objects.equals(f.getSop().getId(), id)) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

        var res = new ByteArrayResource(f.getData());
        var ct = MediaTypeFactory.getMediaType(f.getFileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.getFileName() + "\"")
                .contentLength(f.getSize())
                .contentType(ct)
                .body(res);
    }

    /* ========= Helpers ========= */
    private void saveFilesToDb(SopDocument d, MultipartFile[] uploads) throws Exception {
        if(uploads == null) return;
        var list = Arrays.stream(uploads).filter(f->f!=null && !f.isEmpty()).toList();
        if(list.isEmpty()) return;

        for(var up: list){
            var sf = new SopFile();
            sf.setSop(d);
            sf.setFileName(sanitize(up.getOriginalFilename()==null ? ("file-"+System.currentTimeMillis()) : up.getOriginalFilename()));
            sf.setMime(Optional.ofNullable(up.getContentType()).orElse("application/octet-stream"));
            sf.setSize(up.getSize());
            sf.setData(up.getBytes()); // simpan sebagai BLOB (bytea)
            files.save(sf);
        }
    }

    private static String sanitize(String name){ return name.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private static LocalDate parseDateOrNull(String s){ return (s==null||s.isBlank())?null:LocalDate.parse(s); }
    private static Set<String> parseTags(String csv){
        if(csv==null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(t->!t.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    private static String blankToNull(String s){ return (s==null||s.isBlank())?null:s; }

}
