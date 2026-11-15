package io.github.erickvaernet.starwars.catalog.people;

/**
 * The examples that the API documentation shows for the answer of the list of characters: the same
 * page in its two forms, as the API answers it for {@code size=2}.
 *
 * <p>An element of a list is "one of two", its summary or its complete data, and the example
 * that the documentation builds by itself from that puts one of each in the same page, which the
 * API never answers. These two replace it.
 */
final class PeopleListExamples {

    /** Without {@code expanded}, or with {@code expanded=false}: the summary of each element. */
    static final String SUMMARIES = """
            {
              "content": [
                { "id": "1", "name": "Luke Skywalker" },
                { "id": "2", "name": "C-3PO" }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 82,
              "totalPages": 41
            }""";

    /** With {@code expanded=true}: the same elements, each with its complete data. */
    static final String COMPLETE_ELEMENTS = """
            {
              "content": [
                {
                  "id": "1",
                  "name": "Luke Skywalker",
                  "birthYear": "19BBY",
                  "gender": "male",
                  "height": "172",
                  "mass": "77",
                  "hairColor": "blond",
                  "skinColor": "fair",
                  "eyeColor": "blue",
                  "filmIds": ["1", "2", "3", "6"],
                  "starshipIds": ["12", "22"],
                  "vehicleIds": ["14", "30"]
                },
                {
                  "id": "2",
                  "name": "C-3PO",
                  "birthYear": "112BBY",
                  "gender": "n/a",
                  "height": "167",
                  "mass": "75",
                  "hairColor": "n/a",
                  "skinColor": "gold",
                  "eyeColor": "yellow",
                  "filmIds": ["1", "2", "3", "4", "5", "6"],
                  "starshipIds": [],
                  "vehicleIds": []
                }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 82,
              "totalPages": 41
            }""";

    private PeopleListExamples() {
    }

}
