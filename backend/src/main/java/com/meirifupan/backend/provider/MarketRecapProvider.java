package com.meirifupan.backend.provider;

import com.meirifupan.backend.model.DailyRecapReport;

import java.time.LocalDate;

public interface MarketRecapProvider {

    String name();

    DailyRecapReport capture(LocalDate tradeDate);
}
