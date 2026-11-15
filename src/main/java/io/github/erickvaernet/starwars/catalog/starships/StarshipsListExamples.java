package io.github.erickvaernet.starwars.catalog.starships;

/**
 * The examples that the API documentation shows for the answer of the list of starships: the same
 * page in its two forms, as the API answers it for {@code size=2}.
 *
 * <p>An element of a list is "one of two", its summary or its complete data, and the example
 * that the documentation builds by itself from that puts one of each in the same page, which the
 * API never answers. These two replace it.
 */
final class StarshipsListExamples {

    /** Without {@code expanded}, or with {@code expanded=false}: the summary of each element. */
    static final String SUMMARIES = """
            {
              "content": [
                { "id": "2", "name": "CR90 corvette" },
                { "id": "3", "name": "Star Destroyer" }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 36,
              "totalPages": 18
            }""";

    /** With {@code expanded=true}: the same elements, each with its complete data. */
    static final String COMPLETE_ELEMENTS = """
            {
              "content": [
                {
                  "id": "2",
                  "name": "CR90 corvette",
                  "model": "CR90 corvette",
                  "starshipClass": "corvette",
                  "manufacturer": "Corellian Engineering Corporation",
                  "costInCredits": "3500000",
                  "length": "150",
                  "crew": "30-165",
                  "passengers": "600",
                  "maxAtmospheringSpeed": "950",
                  "hyperdriveRating": "2.0",
                  "mglt": "60",
                  "cargoCapacity": "3000000",
                  "consumables": "1 year",
                  "filmIds": ["1", "3", "6"],
                  "pilotIds": []
                },
                {
                  "id": "3",
                  "name": "Star Destroyer",
                  "model": "Imperial I-class Star Destroyer",
                  "starshipClass": "Star Destroyer",
                  "manufacturer": "Kuat Drive Yards",
                  "costInCredits": "150000000",
                  "length": "1,600",
                  "crew": "47,060",
                  "passengers": "n/a",
                  "maxAtmospheringSpeed": "975",
                  "hyperdriveRating": "2.0",
                  "mglt": "60",
                  "cargoCapacity": "36000000",
                  "consumables": "2 years",
                  "filmIds": ["1", "2", "3"],
                  "pilotIds": []
                }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 36,
              "totalPages": 18
            }""";

    private StarshipsListExamples() {
    }

}
