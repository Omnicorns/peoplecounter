package com.sarinah.peoplecounter.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.peoplecounter.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sarinah-forwarder/v1/modul")
public class ScanBarcodeController {
    private final PostScanBarcodeService postScanBarcodeService;
    private final PostLoyaltyMemberService postLoyaltyMemberService;
    private final PostPosHistoryService postPosHistoryService;
    private final PostJournalEntriesService postJournalEntriesService;
    private final PostOrderRatingService postOrderRatingService;

    @PostMapping(value = "/barcode")
    public ObjectNode postScanResponse(@RequestBody ObjectNode request) {
        return postScanBarcodeService.execute(request);
    }

    @PostMapping(value = "/member-loyalty")
    public ArrayNode postLoyaltyMemberResponse(@RequestBody ObjectNode request) {
        return postLoyaltyMemberService.execute(request);
    }

    @PostMapping(value = "/pos-order-history")
    public ArrayNode postOrderHistoryResponse(@RequestBody ObjectNode request) {
        return postPosHistoryService.execute(request);
    }

    @PostMapping(value = "/journal-entries")
    public ArrayNode postJournalEntriesResponse(@RequestBody ObjectNode request) {
        return postJournalEntriesService.execute(request);
    }

    @PostMapping(value = "/rating")
    public ArrayNode postOrderRatingResponse(@RequestBody ObjectNode request) {
        return postOrderRatingService.execute(request);
    }
}
