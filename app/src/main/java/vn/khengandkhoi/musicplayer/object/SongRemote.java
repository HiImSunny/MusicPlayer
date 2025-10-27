package vn.khengandkhoi.musicplayer.object;

public class SongRemote implements java.io.Serializable {
    private String id;
    private String title;
    private String author;
    private String url;
    private String coverUrl;
    private int trackNum;

    public SongRemote() {}

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getUrl() { return url; }
    public String getCoverUrl() { return coverUrl; }
    public int getTrackNum() { return trackNum; }
}
