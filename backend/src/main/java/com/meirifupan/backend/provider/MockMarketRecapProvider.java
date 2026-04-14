package com.meirifupan.backend.provider;

import com.meirifupan.backend.model.DailyRecapReport;
import com.meirifupan.backend.model.MarketStats;
import com.meirifupan.backend.model.SectorRecord;
import com.meirifupan.backend.model.StockRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模拟数据提供者 —— 返回硬编码的演示数据，不访问任何外部接口。
 * <p>
 * 主要用于：
 * <ul>
 *   <li>前端开发时无需启动 Python 环境即可调试界面</li>
 *   <li>演示和测试场景</li>
 * </ul>
 * 在 application.yml 中设置 recap.provider=mock 即可启用。
 */
@Component
public class MockMarketRecapProvider implements MarketRecapProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public DailyRecapReport capture(LocalDate tradeDate) {
        return new DailyRecapReport(
                tradeDate,
                OffsetDateTime.now(),
                new MarketStats(3210, 1826, 154, 42),
                List.of(
                        stock("002123", "Mengwang Tech", "", "6.12%", "13.62", "Telecom Equipment", "AI Apps; Cloud", "1.132B", "7.530B", "", "", "", "", "", "", "broken-limit"),
                        stock("603456", "Jiuzhou Pharma", "", "4.75%", "18.14", "Chemical Pharma", "Innovative Drug", "0.824B", "10.920B", "", "", "", "", "", "", "broken-limit")
                ),
                List.of(
                        stock("001314", "Edo Info", "", "2.11%", "42.61", "", "", "0.641B", "", "", "", "", "", "7.52%", "41.88", "repair"),
                        stock("603912", "Jialitu", "", "-3.42%", "9.60", "", "", "0.405B", "", "", "", "", "", "6.18%", "9.98", "pressure")
                ),
                List.of(
                        stock("600679", "Shanghai Phoenix", "4", "", "14.23", "", "SOE Reform; Cross-border", "0.915B", "4.102B", "SOE Reform", "0.128B", "4.35%", "23.16%", "", "", ""),
                        stock("002703", "Zhejiang Shibao", "3", "", "16.88", "", "ADAS", "1.342B", "5.677B", "ADAS", "0.092B", "3.14%", "18.02%", "", "", "")
                ),
                List.of(
                        stock("600679", "Shanghai Phoenix", "continue", "", "14.23", "", "SOE Reform; Cross-border", "0.915B", "4.102B", "", "0.128B", "4.35%", "23.16%", "", "", ""),
                        stock("603716", "Sail Medical", "stop", "", "10.58", "", "AI Medical", "0.724B", "3.610B", "", "", "1.12%", "16.54%", "", "", "")
                ),
                List.of(
                        stock("301488", "Haoen Auto", "", "", "68.21", "Auto Parts", "ADAS", "0.710B", "2.855B", "ADAS", "0.081B", "", "", "", "", ""),
                        stock("603778", "Guosheng Tech", "", "", "9.41", "Power Equipment", "PV", "0.506B", "3.143B", "PV Rebound", "0.062B", "", "", "", "", "")
                ),
                List.of(
                        stock("600804", "Risk Corp", "", "", "4.01", "", "Cloud", "", "", "Earnings Pressure", "", "", "", "", "", ""),
                        stock("002173", "Med Innov", "", "", "8.12", "", "AI Medical", "", "", "High-level Retreat", "", "", "", "", "", "")
                ),
                List.of(
                        new SectorRecord("ADAS", "6.28%", "Policy and order momentum"),
                        new SectorRecord("Low-altitude Economy", "5.91%", "Event-driven sentiment")
                ),
                List.of(
                        new SectorRecord("Precious Metals", "-2.83%", "Risk-off fade"),
                        new SectorRecord("Coal", "-2.11%", "Dividend board pullback")
                ),
                List.of(
                        stock("300750", "CATL", "", "35.12%", "218.60", "", "Solid-state Battery; Storage", "8.650B", "942.0B", "", "", "", "", "", "", ""),
                        stock("688256", "Cambricon", "", "32.44%", "423.51", "", "AI Chip", "10.420B", "167.8B", "", "", "", "", "", "", "")
                ),
                List.of(
                        stock("600519", "Kweichow Moutai", "", "16.20%", "1768.00", "", "Liquor", "5.211B", "2231.5B", "", "", "", "", "", "", ""),
                        stock("000333", "Midea Group", "", "14.88%", "86.31", "", "Home Appliance", "2.754B", "489.1B", "", "", "", "", "", "", "")
                ),
                Map.of("ADAS", 12, "SOE Reform", 8, "AI Medical", 5),
                name(),
                "Mock dataset for demo only. Replace provider layer with real market data source later."
        );
    }

    private StockRecord stock(
            String code,
            String name,
            String boardHeight,
            String changePercent,
            String price,
            String industry,
            String concept,
            String amount,
            String floatMarketValue,
            String reason,
            String sealAmount,
            String auctionChangePercent,
            String turnoverRate,
            String amplitude,
            String openPrice,
            String extraTag
    ) {
        return new StockRecord(
                code,
                name,
                boardHeight,
                changePercent,
                price,
                industry,
                concept,
                amount,
                floatMarketValue,
                reason,
                sealAmount,
                auctionChangePercent,
                turnoverRate,
                amplitude,
                openPrice,
                extraTag
        );
    }
}
