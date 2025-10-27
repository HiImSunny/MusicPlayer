package vn.khengandkhoi.musicplayer.object;

public class Category {
    private String id;
    private String name;
    private String colorHex; // ví dụ "#FF6A5ACD"
    private String coverUrl; // tùy chọn

    public Category() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public String getColorHex() { return colorHex; }
    public String getCoverUrl() { return coverUrl; }
}

