package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressRendererTest {

    @Test
    void aPickedCountryIsSearchedByItsName() {
        Address address = new Address("Kolpingstraße 1", "Johannesberg", "", "63867", "DE", null);

        assertThat(AddressRenderer.mapsUrl(address))
                .isEqualTo("https://www.google.com/maps/search/Kolpingstra%C3%9Fe+1+Johannesberg+Germany");
    }

    @Test
    void aPlaceNameLeadsTheSearch() {
        Address address = new Address("Milton Hill", "Steventon", "Abingdon", "OX13 6AF", "GB", null);

        assertThat(AddressRenderer.mapsUrl("Milton Hill House", address))
                .isEqualTo("https://www.google.com/maps/search/"
                           + "Milton+Hill+House+Milton+Hill+Steventon+United+Kingdom");
    }

    /** An event stored before countries were picked still searches by what was typed. */
    @Test
    void aStoredNameIsSearchedAsItIs() {
        Address address = new Address("", "Antwerp", "", "", "Belgium", null);

        assertThat(AddressRenderer.mapsUrl(address))
                .isEqualTo("https://www.google.com/maps/search/Antwerp+Belgium");
    }
}
