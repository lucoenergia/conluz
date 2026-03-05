package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import com.opencsv.CSVWriter;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetDatadisConsumptionReportServiceImpl implements GetDatadisConsumptionReportService {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;

    public GetDatadisConsumptionReportServiceImpl(
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
            GetSupplyRepository getSupplyRepository) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public ByteArrayOutputStream getHourlyConsumptionReportAsCsv(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Supply> supplies = getSupplyRepository.findAll();
        List<DatadisConsumption> consumptions = new ArrayList<>();
        for (Supply supply : supplies) {
            consumptions.addAll(
                    getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply, startDate, endDate));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{
                    "cups", "date", "time", "consumptionKWh", "obtainMethod",
                    "surplusEnergyKWh", "generationEnergyKWh", "selfConsumptionEnergyKWh"
            });
            for (DatadisConsumption c : consumptions) {
                writer.writeNext(new String[]{
                        c.getCups(),
                        c.getDate(),
                        c.getTime(),
                        c.getConsumptionKWh() != null ? c.getConsumptionKWh().toString() : "",
                        c.getObtainMethod(),
                        c.getSurplusEnergyKWh() != null ? c.getSurplusEnergyKWh().toString() : "",
                        c.getGenerationEnergyKWh() != null ? c.getGenerationEnergyKWh().toString() : "",
                        c.getSelfConsumptionEnergyKWh() != null ? c.getSelfConsumptionEnergyKWh().toString() : ""
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }

        return out;
    }
}
