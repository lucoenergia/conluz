package org.lucoenergia.conluz.infrastructure.admin.supply;

import java.util.List;

public class SharingAgreementListResponse {

    private final int total;
    private final int active;
    private final int previous;
    private final List<SharingAgreementResponse> items;

    public SharingAgreementListResponse(int total, int active, int previous,
                                        List<SharingAgreementResponse> items) {
        this.total = total;
        this.active = active;
        this.previous = previous;
        this.items = items;
    }

    public int getTotal() {
        return total;
    }

    public int getActive() {
        return active;
    }

    public int getPrevious() {
        return previous;
    }

    public List<SharingAgreementResponse> getItems() {
        return items;
    }
}
