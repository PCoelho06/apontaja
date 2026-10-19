package com.apontaja.back.customer.application;

import java.util.List;

public record CustomerHistoryPage(List<CustomerHistoryItem> items, int page, int size, long totalElements) {
}
