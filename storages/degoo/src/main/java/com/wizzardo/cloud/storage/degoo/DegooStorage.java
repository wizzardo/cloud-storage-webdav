package com.wizzardo.cloud.storage.degoo;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.tools.http.Request;
import com.wizzardo.tools.http.Response;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;
import com.wizzardo.tools.misc.Stopwatch;

public class DegooStorage implements Storage<DegooFile> {

    protected String username;
    protected String password;
    protected String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySUQiOiIyNzc0NTQ1NCIsImRldmljZUlEIjoiMzI1MTk1MzMiLCJuYmYiOjE2NTAwNDkyNDEsImV4cCI6MTY1MDA1Mjg0MSwiaWF0IjoxNjUwMDQ5MjQxLCJpc3MiOiJkZWdvby5jb20iLCJhdWQiOiJkZWdvby5jb20ifQ._ZeOPF_IhbOpobFQ0jfCanw3LUYHlVIjF6nzMEu73uw";

    protected DegooStorage() {
    }

    public static void main(String[] args) throws IOException {
        DegooStorage storage = new DegooStorage("moxathedark@gmail.com", "qwerty123");
        List<DegooFile> root = storage.list();
        System.out.println(root);
        List<DegooFile> files = storage.list(root.get(0));
        for (DegooFile file : files) {
            System.out.println(file);
        }
    }

    public DegooStorage(String username, String password) {
        this.username = username;
        this.password = password;
    }


    public static class GraphQlResponse {
        List<Error> errors;

        public static class Error {
            List<String> path;
            String errorType;
            String message;
        }
    }

    public static class GraphQlListResponse extends GraphQlResponse {

        Data data;

        public static class Data {
            GetFileChildren5 getFileChildren5;

            public static class GetFileChildren5 {
                String NextToken;
                List<Item> Items;

                public static class Item {
                    int Category; // 1 - root; 2 - folder; 3 - file
                    Date CreationTime;
                    String ID;
                    long LastModificationTime;
                    String MetadataID;
                    String MetadataKey;
                    String Name;
                    String FilePath;
                    String URL;
                    String ThumbnailURL;
                    long Size;
                }
            }
        }
    }

    @Override
    public List<DegooFile> list(DegooFile fileInfo) throws IOException {
        String parentId = "-1";
        if (fileInfo != null)
            parentId = fileInfo.id;

        GraphQlListResponse listResponse = makeGraphQlRequest(new JsonObject()
                .append("operationName", "GetFileChildren5")
                .append("query", "query GetFileChildren5(\n" +
                        "    $Token: String!\n" +
                        "    $ParentID: String\n" +
                        "    $AllParentIDs: [String]\n" +
                        "    $Limit: Int!\n" +
                        "    $Order: Int!\n" +
                        "    $NextToken: String\n" +
                        "  ) {\n" +
                        "    getFileChildren5(\n" +
                        "      Token: $Token\n" +
                        "      ParentID: $ParentID\n" +
                        "      AllParentIDs: $AllParentIDs\n" +
                        "      Limit: $Limit\n" +
                        "      Order: $Order\n" +
                        "      NextToken: $NextToken\n" +
                        "    ) {\n" +
                        "      Items {\n" +
                        "        ID\n" +
                        "        MetadataID\n" +
                        "        UserID\n" +
                        "        DeviceID\n" +
                        "        MetadataKey\n" +
                        "        Name\n" +
                        "        FilePath\n" +
                        "        LocalPath\n" +
                        "        LastUploadTime\n" +
                        "        LastModificationTime\n" +
                        "        ParentID\n" +
                        "        Category\n" +
                        "        Size\n" +
                        "        Platform\n" +
                        "        URL\n" +
                        "        ThumbnailURL\n" +
                        "        CreationTime\n" +
                        "        IsSelfLiked\n" +
                        "        Likes\n" +
                        "        IsHidden\n" +
                        "        IsInRecycleBin\n" +
                        "        Description\n" +
                        "        Location2 {\n" +
                        "          Country\n" +
                        "          Province\n" +
                        "          Place\n" +
                        "          GeoLocation {\n" +
                        "            Latitude\n" +
                        "            Longitude\n" +
                        "          }\n" +
                        "        }\n" +
                        "        Data\n" +
                        "        DataBlock\n" +
                        "        CompressionParameters\n" +
                        "        Shareinfo {\n" +
                        "          Status\n" +
                        "          ShareTime\n" +
                        "        }\n" +
                        "        ShareInfo {\n" +
                        "          Status\n" +
                        "          ShareTime\n" +
                        "        }\n" +
                        "        Distance\n" +
                        "        OptimizedURL\n" +
                        "        Country\n" +
                        "        Province\n" +
                        "        Place\n" +
                        "        GeoLocation {\n" +
                        "          Latitude\n" +
                        "          Longitude\n" +
                        "        }\n" +
                        "        Location\n" +
                        "        IsShared\n" +
                        "        ShareTime\n" +
                        "      }\n" +
                        "      NextToken\n" +
                        "    }\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("ParentID", parentId)
                        .append("Token", token)
                        .append("Order", "3")
                        .append("Limit", "100")
                ), GraphQlListResponse.class);

        return listResponse.data.getFileChildren5.Items.stream()
                .map(item -> {
                    DegooFile file = new DegooFile();
                    file.id = item.ID;
                    file.url = item.URL;
                    file.name = item.Name;
                    file.created = item.CreationTime.getTime();
                    file.updated = item.LastModificationTime;
                    file.size = item.Size;
                    if (item.Category == 2)
                        file.type = FileInfo.Type.FOLDER;
                    else if (item.Category == 3)
                        file.type = FileInfo.Type.FILE;
                    else if (item.Category == 1)
                        file.type = FileInfo.Type.ROOT_FOLDER;

                    return file;
                }).collect(Collectors.toList());
    }

    protected <T extends GraphQlResponse> T makeGraphQlRequest(JsonObject payload, Class<T> asClass) throws IOException {
        return makeGraphQlRequest(payload, asClass, true);
    }

    protected <T extends GraphQlResponse> T makeGraphQlRequest(JsonObject payload, Class<T> asClass, boolean retry) throws IOException {
        payload.getAsJsonObject("variables").append("Token", token);

        Response response = new Request("https://production-appsync.degoo.com/graphql")
                .header("x-api-key", "da2-vs6twz5vnjdavpqndtbzg3prra") // hardcoded in js-app: this.API_KEY = this.useProduction() ? "da2-vs6twz5vnjdavpqndtbzg3prra" : "da2-wnero2yqbjggnmenhg4su2jmce"
                .json(payload.toString())
                .post();
        int responseCode = response.getResponseCode();
        String text = response.asString();
        if (responseCode != 200)
            throw new IllegalStateException(responseCode + " " + text);

        System.out.println(text);
        T parse = JsonTools.parse(text, asClass);

        if (parse.errors != null && !parse.errors.isEmpty()) {
            if (retry && parse.errors.size() == 1 && "Unauthorized".equals(parse.errors.get(0).errorType)) {
                refreshToken();
                return makeGraphQlRequest(payload, asClass, false);
            }

            throw new IllegalStateException(parse.errors.get(0).errorType + ": " + parse.errors.get(0).message);
        }

        return parse;
    }

    public static class LoginResponse {
        String Token;
        String RefreshToken;
    }

    protected void refreshToken() throws IOException {
        Stopwatch stopwatch = new Stopwatch("request");
        Response response = new Request("https://loginator8000.herokuapp.com/degoo")
                .json(new JsonObject()
                        .append("username", username)
                        .append("password", password)
                        .toString())
                .post();

        System.out.println(stopwatch);
        System.out.println(response.getResponseCode());
        String json = response.asString();
        System.out.println(json);
        LoginResponse loginResponse = JsonTools.parse(json, LoginResponse.class);
        token = loginResponse.Token;
    }
}
