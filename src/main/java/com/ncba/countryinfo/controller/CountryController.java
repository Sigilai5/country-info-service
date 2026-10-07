package com.ncba.countryinfo.controller;

import java.net.URI;

import com.ncba.countryinfo.dto.CountryInfoResponse;
import com.ncba.countryinfo.dto.CountryRequest;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.dto.PageResponse;
import com.ncba.countryinfo.dto.WsResponse;
import com.ncba.countryinfo.service.CountryManagementService;
import com.ncba.countryinfo.service.CountryResult;
import com.ncba.countryinfo.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Countries", description = "Fetch country info from the SOAP service and manage stored countries")
public class CountryController {

    private final CountryService countryService;
    private final CountryManagementService managementService;

    public CountryController(CountryService countryService, CountryManagementService managementService) {
        this.countryService = countryService;
        this.managementService = managementService;
    }

    @Operation(summary = "Submit a country name",
            description = "Normalizes the name (e.g. \"kenya\" -> \"Kenya\"), resolves its ISO code via the "
                    + "CountryISOCode SOAP operation, fetches the full country info via FullCountryInfo and stores "
                    + "it with its languages. Idempotent: submitting a country that is already stored returns the "
                    + "stored record (200) without calling the SOAP service.")
    @ApiResponse(responseCode = "201", description = "Country fetched and stored")
    @ApiResponse(responseCode = "200", description = "Country was already stored; stored record returned")
    @ApiResponse(responseCode = "400", description = "Missing or invalid name")
    @ApiResponse(responseCode = "404", description = "The SOAP service knows no country by that name")
    @ApiResponse(responseCode = "503", description = "The SOAP service is unavailable (after retries / circuit open)")
    @PostMapping("/api/v1/countries")
    public ResponseEntity<WsResponse<CountryInfoResponse>> createCountry(
            @Valid @RequestBody CountryRequest request) {
        CountryResult result = countryService.processCountry(request.name());
        if (!result.created()) {
            return ResponseEntity.ok(WsResponse.success(HttpStatus.OK, "Country already exists", result.country()));
        }
        URI location = URI.create("/api/v1/countries/" + result.country().id());
        return ResponseEntity.created(location)
                .body(WsResponse.success(HttpStatus.CREATED, "Country information stored", result.country()));
    }

    @Operation(summary = "Fetch all country information",
            description = "Paginated list of stored countries with their languages. Example: "
                    + "?page=0&size=20&sort=name,asc. Sortable fields: id, name, isoCode, capitalCity, "
                    + "continentCode, currencyIsoCode, createdAt, updatedAt. Max page size 100.")
    @ApiResponse(responseCode = "200", description = "Page of countries")
    @ApiResponse(responseCode = "400", description = "Invalid paging or sort parameter")
    @GetMapping("/api/v1/countries")
    public ResponseEntity<WsResponse<PageResponse<CountryInfoResponse>>> getAllCountries(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        PageResponse<CountryInfoResponse> page = managementService.findAll(pageable);
        return ResponseEntity.ok(WsResponse.success(HttpStatus.OK,
                "Fetched " + page.content().size() + " of " + page.totalElements() + " countries", page));
    }

    @Operation(summary = "Fetch country information by ID")
    @ApiResponse(responseCode = "200", description = "Country found")
    @ApiResponse(responseCode = "400", description = "ID is not a positive number")
    @ApiResponse(responseCode = "404", description = "No country with this ID")
    @GetMapping("/api/v1/countries/{id}")
    public ResponseEntity<WsResponse<CountryInfoResponse>> getCountryById(
            @Parameter(description = "Country ID", example = "1") @PathVariable @Positive Long id) {
        return ResponseEntity.ok(WsResponse.success(HttpStatus.OK, "Country found", managementService.findById(id)));
    }

    @Operation(summary = "Update country information",
            description = "Replaces the stored country, including its language list. Send the version from "
                    + "your last GET to make sure you do not overwrite someone else's change (409 if it changed).")
    @ApiResponse(responseCode = "200", description = "Country updated")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "404", description = "No country with this ID")
    @ApiResponse(responseCode = "409", description = "ISO code used by another country, or the record changed since you read it")
    @PutMapping("/api/v1/countries/{id}")
    public ResponseEntity<WsResponse<CountryInfoResponse>> updateCountry(
            @Parameter(description = "Country ID", example = "1") @PathVariable @Positive Long id,
            @Valid @RequestBody CountryUpdateRequest request) {
        return ResponseEntity.ok(WsResponse.success(HttpStatus.OK, "Country updated",
                managementService.update(id, request)));
    }

    @Operation(summary = "Delete country information", description = "Deletes the country and its languages.")
    @ApiResponse(responseCode = "200", description = "Country deleted")
    @ApiResponse(responseCode = "404", description = "No country with this ID")
    @DeleteMapping("/api/v1/countries/{id}")
    public ResponseEntity<WsResponse<Void>> deleteCountry(
            @Parameter(description = "Country ID", example = "1") @PathVariable @Positive Long id) {
        managementService.delete(id);
        return ResponseEntity.ok(WsResponse.success(HttpStatus.OK, "Country " + id + " deleted", null));
    }
}
