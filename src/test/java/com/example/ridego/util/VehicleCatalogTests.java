package com.example.ridego.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleCatalogTests {

    @Test
    void longDistanceFaresKeepReasonableGaps() {
        double sedan = VehicleCatalog.calculateFare(450, "Sedan");
        double suv = VehicleCatalog.calculateFare(450, "SUV");
        double premium = VehicleCatalog.calculateFare(450, "Premium");

        assertThat(suv / sedan).isBetween(1.15, 1.20);
        assertThat(premium / sedan).isBetween(1.25, 1.35);
        assertThat(premium).isGreaterThan(suv);
    }

    @Test
    void vehicleSpecificSpeedsProduceDifferentEtas() {
        double sedanEta = VehicleCatalog.calculateEtaMinutes(100, "Sedan");
        double suvEta = VehicleCatalog.calculateEtaMinutes(100, "SUV");
        double premiumEta = VehicleCatalog.calculateEtaMinutes(100, "Premium");

        assertThat(premiumEta).isLessThan(sedanEta);
        assertThat(sedanEta).isLessThan(suvEta);
    }

    @Test
    void recommendsSmallestSuitableVehicle() {
        assertThat(VehicleCatalog.recommendSmallestSuitable(4, 2).type()).isEqualTo("Sedan");
        assertThat(VehicleCatalog.recommendSmallestSuitable(4, 3).type()).isEqualTo("Premium");
        assertThat(VehicleCatalog.recommendSmallestSuitable(5, 3).type()).isEqualTo("SUV");
        assertThat(VehicleCatalog.recommendSmallestSuitable(7, 1)).isNull();
    }
}
