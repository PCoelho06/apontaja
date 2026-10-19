package com.apontaja.back.customer.application;

import java.util.List;

public record CustomerCardPage(List<CustomerCard> cards, int page, int size, long totalElements) {
}
