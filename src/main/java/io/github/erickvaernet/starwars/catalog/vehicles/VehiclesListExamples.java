package io.github.erickvaernet.starwars.catalog.vehicles;

/**
 * The examples that the API documentation shows for the answer of the list of vehicles: the same
 * page in its two forms, as the API answers it for {@code size=2}.
 *
 * <p>An element of a list is "one of two", its summary or its complete data, and the example
 * that the documentation builds by itself from that puts one of each in the same page, which the
 * API never answers. These two replace it.
 */
final class VehiclesListExamples {

    /** Without {@code expanded}, or with {@code expanded=false}: the summary of each element. */
    static final String SUMMARIES = """
            {
              "content": [
                { "id": "4", "name": "Sand Crawler" },
                { "id": "7", "name": "X-34 landspeeder" }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 39,
              "totalPages": 20
            }""";

    /** With {@code expanded=true}: the same elements, each with its complete data. */
    static final String COMPLETE_ELEMENTS = """
            {
              "content": [
                {
                  "id": "4",
                  "name": "Sand Crawler",
                  "model": "Digger Crawler",
                  "vehicleClass": "wheeled",
                  "manufacturer": "Corellia Mining Corporation",
                  "costInCredits": "150000",
                  "length": "36.8",
                  "crew": "46",
                  "passengers": "30",
                  "maxAtmospheringSpeed": "30",
                  "cargoCapacity": "50000",
                  "consumables": "2 months",
                  "filmIds": ["1", "5"],
                  "pilotIds": []
                },
                {
                  "id": "7",
                  "name": "X-34 landspeeder",
                  "model": "X-34 landspeeder",
                  "vehicleClass": "repulsorcraft",
                  "manufacturer": "SoroSuub Corporation",
                  "costInCredits": "10550",
                  "length": "3.4",
                  "crew": "1",
                  "passengers": "1",
                  "maxAtmospheringSpeed": "250",
                  "cargoCapacity": "5",
                  "consumables": "unknown",
                  "filmIds": ["1"],
                  "pilotIds": []
                }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 39,
              "totalPages": 20
            }""";

    private VehiclesListExamples() {
    }

}
