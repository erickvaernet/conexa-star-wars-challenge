package io.github.erickvaernet.starwars.catalog.films;

/**
 * The examples that the API documentation shows for the answer of the list of films: the same
 * page in its two forms, as the API answers it for {@code size=2}. A summary calls the title of
 * a film {@code name}; its complete data calls it {@code title}.
 *
 * <p>A line of an example that ends with a backslash goes on in the next one, which starts at
 * the left edge of the example so that it adds no spaces: the opening text of a film is one long
 * text.
 *
 * <p>An element of a list is "one of two", its summary or its complete data, and the example
 * that the documentation builds by itself from that puts one of each in the same page, which the
 * API never answers. These two replace it.
 */
final class FilmsListExamples {

    /** Without {@code expanded}, or with {@code expanded=false}: the summary of each element. */
    static final String SUMMARIES = """
            {
              "content": [
                { "id": "1", "name": "A New Hope" },
                { "id": "2", "name": "The Empire Strikes Back" }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 6,
              "totalPages": 3
            }""";

    /** With {@code expanded=true}: the same elements, each with its complete data. */
    static final String COMPLETE_ELEMENTS = """
            {
              "content": [
                {
                  "id": "1",
                  "title": "A New Hope",
                  "episodeId": 4,
                  "openingCrawl": "It is a period of civil war.\\r\\nRebel spaceships, striking\\r\\nfrom a \
            hidden base, have won\\r\\ntheir first victory against\\r\\nthe evil Galactic \
            Empire.\\r\\n\\r\\nDuring the battle, Rebel\\r\\nspies managed to steal \
            secret\\r\\nplans to the Empire's\\r\\nultimate weapon, the DEATH\\r\\nSTAR, an armored \
            space\\r\\nstation with enough power\\r\\nto destroy an entire \
            planet.\\r\\n\\r\\nPursued by the Empire's\\r\\nsinister agents, Princess\\r\\nLeia \
            races home aboard her\\r\\nstarship, custodian of the\\r\\nstolen plans that can save \
            her\\r\\npeople and restore\\r\\nfreedom to the galaxy....",
                  "director": "George Lucas",
                  "producer": "Gary Kurtz, Rick McCallum",
                  "releaseDate": "1977-05-25",
                  "characterIds": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "12", "13", "14", "15", \
            "16", "18", "19", "81"],
                  "starshipIds": ["2", "3", "5", "9", "10", "11", "12", "13"],
                  "vehicleIds": ["4", "6", "7", "8"]
                },
                {
                  "id": "2",
                  "title": "The Empire Strikes Back",
                  "episodeId": 5,
                  "openingCrawl": "It is a dark time for the\\r\\nRebellion. Although the Death\\r\\nStar has \
            been destroyed,\\r\\nImperial troops have driven the\\r\\nRebel forces from their \
            hidden\\r\\nbase and pursued them across\\r\\nthe galaxy.\\r\\n\\r\\nEvading the \
            dreaded Imperial\\r\\nStarfleet, a group of freedom\\r\\nfighters led by Luke \
            Skywalker\\r\\nhas established a new secret\\r\\nbase on the remote ice world\\r\\nof \
            Hoth.\\r\\n\\r\\nThe evil lord Darth Vader,\\r\\nobsessed with finding \
            young\\r\\nSkywalker, has dispatched\\r\\nthousands of remote probes into\\r\\nthe far \
            reaches of space....",
                  "director": "Irvin Kershner",
                  "producer": "Gary Kurtz, Rick McCallum",
                  "releaseDate": "1980-05-17",
                  "characterIds": ["1", "2", "3", "4", "5", "10", "13", "14", "18", "20", "21", "22", "23", \
            "24", "25", "26"],
                  "starshipIds": ["3", "10", "11", "12", "15", "17", "21", "22", "23"],
                  "vehicleIds": ["8", "14", "16", "18", "19", "20"]
                }
              ],
              "page": 1,
              "size": 2,
              "totalElements": 6,
              "totalPages": 3
            }""";

    private FilmsListExamples() {
    }

}
