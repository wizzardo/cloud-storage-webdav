package com.wizzardo.cloud.storage.degoo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.http.ContentType;
import com.wizzardo.tools.http.Request;
import com.wizzardo.tools.http.Response;
import com.wizzardo.tools.io.FileTools;
import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;
import com.wizzardo.tools.misc.Stopwatch;
import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.security.Base64;
import com.wizzardo.tools.security.SHA1;

public class DegooStorage implements Storage<DegooFile> {

    protected String username;
    protected String password;
    protected String token;
    protected long totalSpace;
    protected long usedSpace;
    protected Cache<String, DegooFile> filesByPath = new Cache<>(600);
    protected Cache<String, DegooFile> filesById = new Cache<>(600);
    protected Cache<String, List<DegooFile>> filesByParentId = new Cache<>(60);

    protected DegooStorage() {
        token = Unchecked.ignore(() -> FileTools.text("degoo.token"), "");
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
        this();
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

    public static class GraphQlUserInfoResponse extends GraphQlResponse {

        Data data;

        public static class Data {
            GetUserInfo3 getUserInfo3;

            public static class GetUserInfo3 {
                String ID;
                String Email;
                long TotalQuota;
                long UsedQuota;
            }
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

    protected GraphQlUserInfoResponse.Data.GetUserInfo3 getUserInfo() throws IOException {
        GraphQlUserInfoResponse response = makeGraphQlRequest(GraphQlUserInfoResponse.class, new JsonObject()
                .append("query", "query GetUserInfo3($Token: String!) {\n" +
                        "    getUserInfo3(Token: $Token) {\n" +
                        "      ID\n" +
                        "      FirstName\n" +
                        "      LastName\n" +
                        "      Email\n" +
                        "      AvatarURL\n" +
                        "      CountryCode\n" +
                        "      LanguageCode\n" +
                        "      Phone\n" +
                        "      AccountType\n" +
                        "      UsedQuota\n" +
                        "      TotalQuota\n" +
                        "      OAuth2Provider\n" +
                        "      GPMigrationStatus\n" +
                        "      FeatureNoAds\n" +
                        "      FeatureTopSecret\n" +
                        "      FeatureDownsampling\n" +
                        "      FeatureAutomaticVideoUploads\n" +
                        "    }\n" +
                        "  }")
        );

        return response.data.getUserInfo3;
    }

    @Override
    public List<DegooFile> list(DegooFile fileInfo) throws IOException {
        String parentId = "-1";
        if (fileInfo != null)
            parentId = fileInfo.id;

        {
            List<DegooFile> list = filesByParentId.get(parentId);
            if (list != null)
                return list;
        }

        GraphQlListResponse listResponse = makeGraphQlRequest(GraphQlListResponse.class, new JsonObject()
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
                ));

        List<DegooFile> list = listResponse.data.getFileChildren5.Items.stream()
                .map(item -> {
                    DegooFile file = new DegooFile();
                    file.id = item.ID;
                    file.url = item.URL;
                    file.path = item.FilePath;
                    file.name = item.Name;
                    file.created = item.CreationTime.getTime();
                    file.updated = item.LastModificationTime;
                    file.size = item.Size;
                    if (item.Category == 2)
                        file.type = FileInfo.Type.FOLDER;
                    else if (item.Category == 1)
                        file.type = FileInfo.Type.ROOT_FOLDER;
                    else // 3 - image/jpg, 5 - audio/mp3
                        file.type = FileInfo.Type.FILE;

                    return file;
                })
                .peek(it -> {
                    filesById.put(it.id, it);
                    filesByPath.put(it.path, it);
                })
                .collect(Collectors.toList());

        filesByParentId.put(parentId, list);
        return list;
    }

    @Override
    public InputStream getStream(DegooFile file) throws IOException {
        Request request = new Request(file.url)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");

        Response response = request.get();
        return response.asStream();
    }

    @Override
    public DegooFile getInfo(String path) throws IOException {
        DegooFile file = filesByPath.get(path);
        if (file != null)
            return file;

        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        int i = path.lastIndexOf("/");
        if (i == -1) {
            if (path.isEmpty()) {
                list();
                return filesByPath.get(path);
            }
            throw new IllegalArgumentException();
        }

        String name = path.substring(i + 1);
        if (name.startsWith("."))
            return null;

        String parent = path.substring(0, i);
        DegooFile parentInfo = getInfo(parent);
        if (parentInfo == null)
            return null;

        if ("/".equals(path)) {
            return parentInfo;
        }

        list(parentInfo);
        return filesByPath.get(path);
    }

    @Override
    public byte[] getData(DegooFile file, long from, long to) throws IOException {
        Request request = new Request(file.url)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");

        if (from != 0 || to != file.size) {
            request.header("Range", "bytes=" + from + "-" + (to - 1));
        }

        Response response = request.get();
        byte[] bytes = response.asBytes();
        return bytes;
    }

    @Override
    public long getTotalSpace() throws IOException {
        if (totalSpace == 0) {
            refreshQuotaInfo();
        }
        return totalSpace;
    }

    @Override
    public long getUsableSpace() throws IOException {
        if (totalSpace == 0) {
            refreshQuotaInfo();
        }
        return totalSpace - usedSpace;
    }

    private void refreshQuotaInfo() throws IOException {
        GraphQlUserInfoResponse.Data.GetUserInfo3 info = getUserInfo();
        totalSpace = info.TotalQuota;
        usedSpace = info.UsedQuota;
    }

    protected <T extends GraphQlResponse> T makeGraphQlRequest(Class<T> asClass, JsonObject payload) throws IOException {
        return makeGraphQlRequest(asClass, payload, true);
    }

    protected <T extends GraphQlResponse> T makeGraphQlRequest(Class<T> asClass, JsonObject payload, boolean retry) throws IOException {
        JsonObject variables = payload.getAsJsonObject("variables");
        if (variables == null)
            payload.append("variables", variables = new JsonObject());

        variables.append("Token", token);

        Response response = new Request("https://production-appsync.degoo.com/graphql")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
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
                return makeGraphQlRequest(asClass, payload, false);
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
        FileTools.text("degoo.token", loginResponse.Token);
    }

    @Override
    public void createFolder(String path) throws IOException {
        int nameSeparator = path.lastIndexOf('/');
        String name = path.substring(nameSeparator + 1);
        String folder = path.substring(0, nameSeparator);

        DegooFile parent = getInfo(folder);
        if (parent == null) {
            createFolder(folder);
            parent = getInfo(folder);
        }

        createFolder(name, parent.id);
    }

    @Override
    public void delete(DegooFile path) throws IOException {
        deleteFile(path.id);
    }

    @Override
    public void put(String path, byte[] bytes) throws IOException {
        int nameSeparator = path.lastIndexOf('/');
        String name = path.substring(nameSeparator + 1);
        String folder = path.substring(0, nameSeparator);

        DegooFile folderInfo = getInfo(folder);
        upload(new ByteArrayUpload(bytes), name, folderInfo.id);
    }

    @Override
    public void put(String path, File file) throws IOException {
        int nameSeparator = path.lastIndexOf('/');
        String name = path.substring(nameSeparator + 1);
        String folder = path.substring(0, nameSeparator);

        DegooFile folderInfo = getInfo(folder);
        upload(new FileUpload(file), name, folderInfo.id);
    }

    @Override
    public void move(DegooFile file, String destination) throws IOException {
        int nameSeparator = destination.lastIndexOf('/');
        String name = destination.substring(nameSeparator + 1);
        String folder = destination.substring(0, nameSeparator);

        DegooFile folderInfo = getInfo(folder);
        if (folderInfo == null)
            throw new IllegalArgumentException("Parent folder doesn't exist");

        moveFile(file.id, folderInfo.id);
        if (!name.equals(file.name))
            renameFile(file.id, name);
    }

    static class SetDeleteFile5Response extends GraphQlResponse {

        Data data;

        static class Data {
            boolean setDeleteFile5;
        }
    }

    public void deleteFile(String fileId) throws IOException {
        SetDeleteFile5Response setDeleteFile5Response = makeGraphQlRequest(SetDeleteFile5Response.class, new JsonObject()
                .append("operationName", "SetDeleteFile5")
                .append("query", "mutation SetDeleteFile5(\n" +
                        "    $Token: String!\n" +
                        "    $IsInRecycleBin: Boolean!\n" +
                        "    $IDs: [IDType]!\n" +
                        "  ) {\n" +
                        "    setDeleteFile5(Token: $Token, IsInRecycleBin: $IsInRecycleBin, IDs: $IDs)\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("IDs", new JsonArray().append(new JsonObject().append("FileID", fileId)))
                        .append("IsInRecycleBin", false)
                )
        );
        if (setDeleteFile5Response.data == null || !setDeleteFile5Response.data.setDeleteFile5)
            throw new IllegalStateException("Deletion failed");
    }

    static class SetMoveFileResponse extends GraphQlResponse {

        Data data;

        static class Data {
            boolean setDeleteFile5;
        }
    }

    public void moveFile(String fileId, String toFolderId) throws IOException {
        SetMoveFileResponse setDeleteFile5Response = makeGraphQlRequest(SetMoveFileResponse.class, new JsonObject()
                .append("operationName", "SetMoveFile")
                .append("query", "mutation SetMoveFile(\n" +
                        "    $Token: String!\n" +
                        "    $Copy: Boolean\n" +
                        "    $NewParentID: String!\n" +
                        "    $FileIDs: [String]!\n" +
                        "  ) {\n" +
                        "    setMoveFile(\n" +
                        "      Token: $Token\n" +
                        "      Copy: $Copy\n" +
                        "      NewParentID: $NewParentID\n" +
                        "      FileIDs: $FileIDs\n" +
                        "    )\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("FileIDs", new JsonArray().append(fileId))
                        .append("NewParentID", toFolderId)
                        .append("Copy", false)
                )
        );
        if (setDeleteFile5Response.data == null || !setDeleteFile5Response.data.setDeleteFile5)
            throw new IllegalStateException("Deletion failed");
    }

    static class SetUploadFile3Response extends GraphQlResponse {

        Data data;

        static class Data {
            boolean setUploadFile3;
        }
    }

    public void createFolder(String name, String parentId) throws IOException {
        SetUploadFile3Response setUploadFile3Response = makeGraphQlRequest(SetUploadFile3Response.class, new JsonObject()
                .append("operationName", "SetUploadFile3")
                .append("query", "mutation SetUploadFile3($Token: String!, $FileInfos: [FileInfoUpload3]!) {\n" +
                        "    setUploadFile3(Token: $Token, FileInfos: $FileInfos)\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("FileInfos", new JsonArray().append(
                                new JsonObject()
                                        .append("Checksum", "CgAQAg")
                                        .append("CreationTime", String.valueOf(System.currentTimeMillis()))
                                        .append("Name", name)
                                        .append("ParentID", parentId)
                                        .append("Size", 0)
                        ))
                )
        );
        if (setUploadFile3Response.data == null || !setUploadFile3Response.data.setUploadFile3)
            throw new IllegalStateException("Folder creation failed");
    }

    static class SetRenameFileResponse extends GraphQlResponse {

        Data data;

        static class Data {
            boolean setRenameFile;
        }
    }

    public void renameFile(String fileId, String newName) throws IOException {
        SetRenameFileResponse setDeleteFile5Response = makeGraphQlRequest(SetRenameFileResponse.class, new JsonObject()
                .append("operationName", "SetRenameFile")
                .append("query", "mutation SetRenameFile($Token: String!, $FileRenames: [FileRenameInfo]!) {\n" +
                        "    setRenameFile(Token: $Token, FileRenames: $FileRenames)\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("FileRenames", new JsonArray().append(new JsonObject()
                                .append("ID", fileId)
                                .append("NewName", newName)
                        ))
                        .append("IsInRecycleBin", false)
                )
        );
        if (setDeleteFile5Response.data == null || !setDeleteFile5Response.data.setRenameFile)
            throw new IllegalStateException("Renaming failed");
    }


    static class GetBucketWriteAuth4Response extends GraphQlResponse {
        Data data;

        static class Data {
            List<BucketWriteAuth4> getBucketWriteAuth4;

            static class BucketWriteAuth4 {
                AuthData AuthData;
                String Error;

                static class AuthData {
                    String ACL;
                    String BaseURL;
                    String KeyPrefix;
                    String PolicyBase64;
                    String Signature;
                    AccessKey AccessKey;
                    List<KeyValue> AdditionalBody;

                    static class KeyValue {
                        String Key;
                        String Value;
                    }

                    static class AccessKey {
                        String Key;
                        String Value;
                    }
                }
            }
        }
    }

    interface UploadPayload {
        default boolean isFile() {
            return false;
        }

        default boolean isByteArray() {
            return false;
        }

        long size();

        default File asFile() {
            throw new IllegalStateException();
        }

        default byte[] asByteArray() {
            throw new IllegalStateException();
        }
    }

    static class FileUpload implements UploadPayload {
        final File file;

        FileUpload(File file) {
            this.file = file;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public long size() {
            return file.length();
        }

        @Override
        public File asFile() {
            return file;
        }
    }

    static class ByteArrayUpload implements UploadPayload {
        final byte[] bytes;

        ByteArrayUpload(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean isByteArray() {
            return true;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public byte[] asByteArray() {
            return bytes;
        }
    }

    public void upload(UploadPayload payload, String name, String parentId) throws IOException {
        String checksum = getChecksum(payload);
        GetBucketWriteAuth4Response getBucketWriteAuth4Response = makeGraphQlRequest(GetBucketWriteAuth4Response.class, new JsonObject()
                .append("operationName", "GetBucketWriteAuth4")
                .append("query", "query GetBucketWriteAuth4(\n" +
                        "    $Token: String!\n" +
                        "    $ParentID: String!\n" +
                        "    $StorageUploadInfos: [StorageUploadInfo2]\n" +
                        "  ) {\n" +
                        "    getBucketWriteAuth4(\n" +
                        "      Token: $Token\n" +
                        "      ParentID: $ParentID\n" +
                        "      StorageUploadInfos: $StorageUploadInfos\n" +
                        "    ) {\n" +
                        "      AuthData {\n" +
                        "        PolicyBase64\n" +
                        "        Signature\n" +
                        "        BaseURL\n" +
                        "        KeyPrefix\n" +
                        "        AccessKey {\n" +
                        "          Key\n" +
                        "          Value\n" +
                        "        }\n" +
                        "        ACL\n" +
                        "        AdditionalBody {\n" +
                        "          Key\n" +
                        "          Value\n" +
                        "        }\n" +
                        "      }\n" +
                        "      Error\n" +
                        "    }\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("ParentID", parentId)
                        .append("Token", token)
                        .append("StorageUploadInfos", new JsonArray()
                                .append(new JsonObject()
                                        .append("Checksum", checksum)
                                        .append("FileName", name)
                                        .append("Size", payload.size())
                                )
                        )
                )
        );


        GetBucketWriteAuth4Response.Data.BucketWriteAuth4 bucketWriteAuth4 = getBucketWriteAuth4Response.data.getBucketWriteAuth4.get(0);
        if (bucketWriteAuth4.AuthData == null || bucketWriteAuth4.Error != null)
            return;

        String extension = name;
        extension = extension.substring(extension.lastIndexOf('.') + 1);

        GetBucketWriteAuth4Response.Data.BucketWriteAuth4.AuthData authData = bucketWriteAuth4.AuthData;
        Request request = new Request(authData.BaseURL)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                .param("key", authData.KeyPrefix + extension + "/" + checksum + "." + extension)
                .param("acl", authData.ACL)
                .param("policy", authData.PolicyBase64)
                .param("signature", authData.Signature)
                .param(authData.AccessKey.Key, authData.AccessKey.Value);
        for (GetBucketWriteAuth4Response.Data.BucketWriteAuth4.AuthData.KeyValue keyValue : authData.AdditionalBody) {
            request.param(keyValue.Key, keyValue.Value);
        }

        request.param("Content-Type", ContentType.BINARY);
        if (payload.isFile())
            request.addFile("file", payload.asFile().getCanonicalPath());
        else if (payload.isByteArray())
            request.addByteArray("file", payload.asByteArray(), name);
        else
            throw new IllegalArgumentException("Unknown payload type");

        Response response = request.post();

        System.out.println(response.getResponseCode());
        System.out.println(response.asString());

        if (response.getResponseCode() != 204)
            return;

        SetUploadFile3 setUploadFile3 = makeGraphQlRequest(SetUploadFile3.class, new JsonObject()
                .append("operationName", "SetUploadFile3")
                .append("query", "mutation SetUploadFile3($Token: String!, $FileInfos: [FileInfoUpload3]!) {\n" +
                        "    setUploadFile3(Token: $Token, FileInfos: $FileInfos)\n" +
                        "  }")
                .append("variables", new JsonObject()
                        .append("Token", token)
                        .append("FileInfos", new JsonArray()
                                .append(new JsonObject()
                                        .append("Checksum", checksum)
                                        .append("Name", name)
                                        .append("Size", payload.size())
                                        .append("ParentID", parentId)
                                        .append("CreationTime", System.currentTimeMillis())
                                )
                        )
                )
        );

        if (setUploadFile3.data == null || !setUploadFile3.data.setUploadFile3)
            throw new IllegalStateException("Upload failed");
    }

    static class SetUploadFile3 extends GraphQlResponse {

        Data data;

        static class Data {
            boolean setUploadFile3;
        }
    }


    public static String getChecksum(UploadPayload payload) throws IOException {
        if (payload.isFile())
            return getChecksum(payload.asFile());
        if (payload.isByteArray())
            return getChecksum(payload.asByteArray());
        throw new IllegalArgumentException("Unknown payload type");
    }

    public static String getChecksum(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] bytes = SHA1.create()
                    .update(new byte[]{13, 7, 2, 2, 15, 40, 75, 117, 13, 10, 19, 16, 29, 23, 3, 36})
                    .update(in)
                    .asBytes();
            return serializeChecksum(bytes);
        }
    }

    public static String getChecksum(byte[] data) {
        byte[] bytes = SHA1.create()
                .update(new byte[]{13, 7, 2, 2, 15, 40, 75, 117, 13, 10, 19, 16, 29, 23, 3, 36})
                .update(data)
                .asBytes();
        return serializeChecksum(bytes);
    }

    private static String serializeChecksum(byte[] bytes) {
        byte[] checksum = new byte[24];
        checksum[0] = 10; // hardcoded
        checksum[1] = 20; // length?
        System.arraycopy(bytes, 0, checksum, 2, 20);
        checksum[22] = 16; // hardcoded
        checksum[23] = 0; // type - always 0
        String result = Base64.encodeToString(checksum, false, true);
        result = result.replace("=", "");
        return result;
    }
}
