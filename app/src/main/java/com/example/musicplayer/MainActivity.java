package com.example.musicplayer;

import static android.content.ContentValues.TAG;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
// Unicorn File Picker is a third party library we utilize for file selection.
import abhishekti7.unicorn.filepicker.UnicornFilePicker;
import abhishekti7.unicorn.filepicker.utils.Constants;

/*
    Class MainActivity
        **Override**
        void OnCreate - Runs on App Startup. Contains interface setup, listener setup, and more.
        void toastGeneric(text to show) - displays a popup msg to the user containing the text to show variable.
        void permissionCheck(permission type, return code) - Dynamic method for checking permissions
        void onRequestPermissionResult(int reqCode, String[] permissions, int[] grantResults) - Takes results from permissions check and ensures we have permissions needed
        void openSongEditor - method that initializes and displays the song popup to the user.
        void repopulateCDLL - General purpose method that clears and re initializes the circular doubly linked list if needed.
        void updateCurrentSong - Traverses the current circular doubly linked list in order to find the next song needed if one is deleted.
 */


public class MainActivity extends AppCompatActivity {

    // Permission Check Variable
    private static final int STORAGE_PERMISSION_CODE = 101;

    // Declare our Media Player Early so it can be used everywhere in this class.
    MediaPlayer mp = new MediaPlayer();

    Toast toast;

    boolean genreSpinnerInitialized;
    boolean artistSpinnerInitialized;

    // Boolean for mediaplayer, currently not used due to no ability to play music (Removed for testing)
    boolean isMPStopped = false;

    // This is the arraylist of files the user is trying to import. It does NOT STORE SONGS THE USER HAS ALREADY SELECTED.
    ArrayList<String> songs;

    // The creates our parser so we can use it in the below if/else statements.

    ParseSongList parser = new ParseSongList();

    // This is our array of songs for the recyclerView
    ArrayList<Song> songArr;

    RecyclerView recyclerView;

    // Queried Songs from search :D
   ArrayList<Song> queriedResults;

   // NEW Class-Wide Adapter. Much More efficient. Declared
    // in the onCreate Method Below. Same as new Adapter (Search Results adapter)
    SongAdapter adapter;

    // This is our CDLL that we use to do... Most everything. :D
    // (Props to Alessa for making a great CDLL class)
    CyclicDouble CDLList = new CyclicDouble();
    Node currentSong;

    // Only allows the user to choose popular audio file types.
    String[] filters = {"mp3","ogg","wav","m4a"};

    // Here we declare our handler. It allows us to run things asyncronously on the same
    // or different threads.
    Handler handler = new Handler();

    // This is the image view of our banner. We'll initialize it a bit later.
    ImageView adBanner;
    // Classwide current time variable. Can be changed throughout the activity.
    TextView currentTimePlaying;
    TextView songTitle;
    // The seekbar is the bar you can see the song's current time on.
    SeekBar seekBar;

    // These variables are used to process and run the ad banners.
    // This array holds the integer references for our ads to put them into our ad banner CDLL.
    int[] adBannerPaths = {R.drawable.ad_one, R.drawable.ad_two, R.drawable.ad_three, R.drawable.ad_four,
                           R.drawable.ad_five, R.drawable.ad_seven, R.drawable.ad_eight, R.drawable.ad_nine};
    CyclicDoubleInt adBannerCDLL = new CyclicDoubleInt();

    // This is an integer node because R.*.* returns an int reference to the object.
    CyclicDoubleInt.IntNode currentAd;

    // This runnable is infinite and runs every few seconds to change our banner ad.
    Runnable adRunnable = new Runnable() {
        @Override
        public void run() {
    // Set our image to the next ad in the currentAd CDLL
            currentAd = currentAd.next;
            adBanner.setImageDrawable(getResources().getDrawable(currentAd.data));
            // This works kinda like recursion, the runnable infinitely calls itself because we want the ad to infinitely cycle
            // while the app is open :D
            handler.postDelayed(adRunnable, 8000);
        }
    };

    //This is a time Runnable. It asyncrounously loads the time for the mediaplayer every second and displays it on the screen in a textview.
    Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            int timeInSong = mp.getCurrentPosition();
            currentTimePlaying.setText((String.format("%02d", timeInSong / 3600000)) + ":" + String.format("%02d",(timeInSong / 1000 / 60)) + ":" + String.format("%02d", (timeInSong / 1000) % 60));
            handler.postDelayed(timeRunnable, 1000);

        }
    };
    // This Runnable prevents the search from querying too often. This is an optimization technique
    // because the search results change every time the search query is updated. We don't want that.
    boolean tooSoonToSearch = false;
    Runnable preventionRunnable = new Runnable() {
        @Override
        public void run() {
            tooSoonToSearch = !tooSoonToSearch;
            // Infinite Recursive Call
            handler.postDelayed(preventionRunnable, 500);
        }
    };
// This seekbar runnable updates the seek bar every second.
    Runnable seekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if(mp != null) {
                int currentPlace = mp.getCurrentPosition() / 1000;
                seekBar.setProgress(currentPlace);
            }
            handler.postDelayed(seekBarRunnable, 1000);
        }
    };


// This string is important for setting up our title to display.
    String currentlyPlaying;


    // This is the variable for our search bar!
    SearchView searchBar;

    // These variables used to be initialized in the editSongPopup method
    // but we needed them to be usable classwide. So we initialize them in onCreate
    // and declare them here. More info on these variable is available below.
    View editsongView;
    LayoutInflater inflateEdit;
    Spinner genreSpinner;
    ArrayAdapter<String> genreAdapter;
    ArrayList<String> genreOptions;
    Spinner artistSpinner;
    ArrayAdapter<String> artistAdapter;
    ArrayList<String> artistOptions;

    boolean firstLaunch = true;
    View openTutorial;
    Button closeTutBtn;



    // onCreate Method for doing... Everything? The on create method
    // is everything that needs to happen when the app starts.
    // You'll notice it's quite large, and it's very typical for this
    // to be the case.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // inflateEdit is used to grab the instances of our popup windows.
        inflateEdit = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        // These lines will fetch our saved information so we can check if the user has launched the app before.
        Context context = this;
        SharedPreferences sharedPreferences = getSharedPreferences("firstLaunch", MODE_PRIVATE);
        firstLaunch = sharedPreferences.getBoolean("firstLaunch", true);

        // This will launch the tutorial page if the user is a new user.
        if(firstLaunch) {
            Intent intent = new Intent(this, TutorialActivity.class);
            this.startActivity(intent);
        }

        // This is used later when the user long presses a song title.
        editsongView = inflateEdit.inflate(R.layout.edit_popup, null);
        songArr = parser.getEntries();
        // We initialize these classwide variables in the onCreate method because
        // they are not reachable in the class itself.
        // These adapters do not require conditionals to be set because even if they
        // are null, they are not visible to the user until the user adds songs
        // to the playlist which makes the adapters nonnull.
        genreSpinner = editsongView.findViewById(R.id.genreSpinner);
        artistSpinner = editsongView.findViewById(R.id.artistSpinner);

        if(songArr != null) {
            genreOptions = parser.search(true, "genre");
            genreAdapter = new ArrayAdapter<>(this,R.layout.support_simple_spinner_dropdown_item,genreOptions);
            genreSpinner.setAdapter(genreAdapter);

            artistSpinner = editsongView.findViewById(R.id.artistSpinner);
            artistOptions = parser.search(true,"artist");
            artistAdapter = new ArrayAdapter<>(this,R.layout.support_simple_spinner_dropdown_item, artistOptions);
            artistSpinner.setAdapter(artistAdapter);
        }


        // We initialize our recycler view immediately
        recyclerView = findViewById(R.id.songList);
        // Next we initialize our adapter. See the Song Adapter
        // class for more information.
        adapter= new SongAdapter(songArr, new SongAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Song clickedSong) {
                // Test Logs Pre-Traversal of CDLL
               // Log.e("CDLL Test1", currentSong.song.getPath());
               // Log.e("CDLL Test2", clickedSong.getPath());
                // Compare the current song's title to the song that was clicked.
                while(!currentSong.song.getPath().equals(clickedSong.getPath())) {
                        currentSong = currentSong.next;

                }
                // Test Log Post Traversal
                //Log.e("CDLL Test", currentSong.song.getPath());
                // Reset and run our media player
                try {
                    mp.reset();
                    mp.setDataSource(currentSong.song.getPath());
                    mp.prepareAsync();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onLongItemClick(Song item) {
                // Adapters for spinners do not dynamically update like our recycler view song adapter will. therefore, we
                // need to reset them every time the user wants to edit a song.
                genreOptions = parser.search(true, "genre");
                genreAdapter = new ArrayAdapter<>(getApplicationContext(),R.layout.support_simple_spinner_dropdown_item,genreOptions);
                genreSpinner.setAdapter(genreAdapter);
                // Same as above
                artistOptions = parser.search(true, "artist");
                artistAdapter = new ArrayAdapter<>(getApplicationContext(),R.layout.support_simple_spinner_dropdown_item,artistOptions);
                artistSpinner.setAdapter(artistAdapter);
                // Here we open our song editor popup.
                openSongEditor(item);
            }
        });

        // The song title and the currently playing text references.
        songTitle = findViewById(R.id.songTitle);
        TextView currentlyPlayingText = findViewById(R.id.textView2);

        // Allowing the user to rotate their phone resets the activity, and we don't want that.
        // This line locks the user's phone orientation to portrait mode. Many apps do this.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // The permission check method is at the bottom of the Main Activity class.
        // It will see if the user has granted us permissions. If not, we prompt them.
        permissionCheck(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);

        // Just Grabbing our buttons for java.
        Button selectButton = findViewById(R.id.fileSelectBtn);
        ImageButton playButton = findViewById(R.id.playButton);
        ImageButton skipButton = findViewById(R.id.NextBtn);
        ImageButton prevButton = findViewById(R.id.previousBtn);
        ImageButton ffBtn = findViewById(R.id.FFBtn);
        ImageButton rewBtn = findViewById(R.id.rewBtn);

        // Initialize our ad banner variable
        adBanner = findViewById(R.id.rotatingAds);
        // This is the song timer.
        currentTimePlaying = findViewById(R.id.songTime);
        // this is the seekbar variable :D
        seekBar = findViewById(R.id.seekBar);

        // For loop that populates our like... 22nd CDLL at this point.
        // REMEMBER: R.id.* are all integers. We re-use our Int CDLL.
        for(int i = 0; i < adBannerPaths.length; i++) {
            adBannerCDLL.insertNode(adBannerPaths[i]);
        }
        // Set the first ad to the head of our CDLL.
        currentAd = adBannerCDLL.head;
        // This asyncronously runs our delayed code to cycle the CDLL of our ad banners.
        handler.postDelayed(adRunnable,0);
        // Gotta start ALL THE HANDLER RUNNABLES LET'S GOOOOO
        handler.postDelayed(seekBarRunnable,0);
        handler.postDelayed(preventionRunnable,0);


        // Initialize our search bar
        searchBar = findViewById(R.id.searchSongs);
        // This is a listener for our search bar. It
        // Overrides the methods that run when the query changes
        // or when the submit button (or enter) is pressed.
        searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String s) {
                // If the query is empty, then we reset
                // the adapter because we want to show all songs again
                if (s.isEmpty()) {
                    Log.e("CLEARING", "CLEARING SEARCHES...");
                    adapter.updateList(songArr);
                } else if (s != null) {
                    // If the query isn't empty, we need to call our search method.
                    // see Parse Song List for more details.
                    queriedResults = parser.search(s);
                    adapter.updateList(queriedResults);
                }
                return false;
            }

// Same as above except we want this to run if the user presses submit.
            @Override
            public boolean onQueryTextChange(String s) {
                if(s.isEmpty()) {
                    Log.e("CLEARING", "CLEARING SEARCHES...");
                    adapter.updateList(songArr);
                } else if(s != null && !tooSoonToSearch) {
                    queriedResults = parser.search(s);
                    adapter.updateList(queriedResults);
                }
                return false;
            }
        });


        // Quickly Populates our recycler view song list. If there are entries.
        songArr = parser.getEntries();
        if(songArr != null) {
            RecyclerView recyclerView = findViewById(R.id.songList);
            recyclerView.setHasFixedSize(false);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            // Populates our CDLL With songs at startup.
            for (Song song : songArr) {
                CDLList.insertNode(song);
            }
            // If there are songs in our CDLL, we will queue and play them.
            // Remember this is only called if the song array isn't empty
            // which in turn means our CDLList isn't empty either.
            currentSong = CDLList.head;
            // Just a testing Log.
            Log.e("DID POPULATE?:",CDLList.toString());
            adapter.updateList(songArr);
            recyclerView.setAdapter(adapter);
        }
        // Set our music player to play on launch if possible.
        if(CDLList.head != null) {
            try {
                mp.setDataSource(CDLList.head.song.getPath());
                handler.postDelayed(timeRunnable,0);
                // We don't need to call mp.start because we do that in our onPrepared Listener.
                mp.prepareAsync();
            } catch (IOException e) {
            }
        }

        // Our onPrepared listener allows  the mediaplayer to function
        // without having to worry about its states.
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                // Okay so this will start our audio ONCE IT'S READY
                playButton.setImageDrawable(getResources().getDrawable(R.drawable.pause_btn));
                currentlyPlaying = currentSong.song.getTitle();
                songTitle.setText(currentlyPlaying);
                currentlyPlayingText.setText("Currently Playing:");
                seekBar.setMax(mp.getDuration() / 1000);
             //   toastGeneric("The Total Time for this Track is: " + mp.getDuration() / 1000 + " Seconds.");
                mp.start();
            }
        });


        // What happens when the song is done.
        mp.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        mp.reset();
                        try {
                            if (currentSong.next != null) {
                                currentSong = currentSong.next;

                            }
                            String pathToPlay = currentSong.song.getPath();


                            try {
                                mp.setDataSource(pathToPlay);
                                mp.prepareAsync();

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        } catch (Exception e) {

                        }
                    }
                }

        );
        // Our seek bars on change listener. We only utilize the onStopTrackingTouch method.
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {


            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(mp != null) {
                    mp.seekTo(seekBar.getProgress() * 1000);
                }
            }
        });




        // Fast forward button Code.
        ffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // We populate the current song's CDLL of timestamps.
                if (currentSong != null) {
                    currentSong.song.populateFastFoward(mp);
                    // Log.e("CURRENT MP TIME", String.valueOf(mp.getCurrentPosition()));
                    Log.e("FF NODE TIME", String.valueOf(currentSong.song.seekToNode.data));
                    //  Log.e("FFBTN", String.valueOf(mp.getDuration()));
                    // Remember fast forward is a method that does seekToNode = seekToNode.next;
                    currentSong.song.fastFoward(mp);
                    // So this is a little weird.... Let's go piece by piece shall we? Yes.
                    // mp.seekTo goes to that time in the song.
                    // nextSong.song gets the song object that currently exists in the nextSong node.
                    // .seekToNode is our node within the song object.
                    // .data gets that number for us.
                    mp.seekTo(currentSong.song.seekToNode.data);
                    // So these are just some logs that will let everyone know it is indeed skipping
                    // into the future.
                    Log.e("CURRENT MP TIME", String.valueOf(mp.getCurrentPosition()));
                //    toastGeneric("The Current Time of the Song is: " + String.valueOf(mp.getCurrentPosition() / 1000) + " seconds.");
                } else {
                    toastGeneric("There is no song playing right now.");
                }
            }
        });
        // This is the long click listener. press the button, it clears the fast forward CDLL and makes a new one.
        // CDLLs reproduce by long clicking.
        ffBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                // Delete all of the current nodes.
                if (currentSong != null) {
                    currentSong.song.skipTimeCDLL.deleteAllNodes();
                    // Repopulate the song's CDLL
                    currentSong.song.populateFastFoward(mp);
                    // Sett the time  to where it needs to go in the future
                    // (We're assuming the user wanted to fast forward on the
                    // long press.
                    currentSong.song.fastFoward(mp);
                    // Go to that point in the song
                    mp.seekTo(currentSong.song.seekToNode.data);
                    // So apparently you have to return a boolean with on long click listeners.
                } else {
                    toastGeneric("There is no song playing right now.");
                }
                return true;
            }

        });

        // See above, I'm not typing it again.
        rewBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if(currentSong != null) {
                    currentSong.song.skipTimeCDLL.deleteAllNodes();
                    currentSong.song.populateRewind(mp);
                    currentSong.song.rewind();
                    mp.seekTo(currentSong.song.seekToNode.data);
                } else {
                    toastGeneric("There is no song playing right now.");
                }
                return true;
            }

        });


        // See above, I'm not typing it again.
        rewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(currentSong != null) {
                    //  Log.e("CURRENT MP TIME", String.valueOf(mp.getCurrentPosition()));
                    currentSong.song.populateRewind(mp);
                    Log.e("RW NODE TIME", String.valueOf(currentSong.song.seekToNode.data));
                    //  Log.e("RWBTN", String.valueOf(mp.getDuration()));
                    currentSong.song.rewind();
                    mp.seekTo(currentSong.song.seekToNode.data);
                    Log.e("CURRENT MP TIME", String.valueOf(mp.getCurrentPosition()));
                  //  toastGeneric("The Current Time of the Song is: " + String.valueOf((mp.getCurrentPosition() / 1000)) + " seconds.");
                } else {
                    toastGeneric("There is no song playing right now.");
                }
            }
        });




        // This is what happens when our play button is pressed.
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentSong != null) {
                    try {
                        // This is our pause functionality here
                        if (mp.isPlaying()) {
                            playButton.setImageDrawable(getDrawable(R.drawable.play_btn));
                            mp.pause();
                            // This variable needed to be changed here for some reason
                            // I forgot why
                            isMPStopped = false;
                        } else {
                            if (!isMPStopped) {
                                //   Log.e("MP PLAY BTN", "MP PLAY BUTTON KNEW MP WAS NOT STOPPED");
                                playButton.setImageDrawable(getDrawable(R.drawable.pause_btn));
                                mp.start();
                            } else {
                                //   Log.e("MP PLAY BUTTON","MP PLAY BUTTON SAYS MP WAS STOPPED");
                                String pathToPlay = currentSong.song.getPath();
                                mp.setDataSource(pathToPlay);
                                mp.prepareAsync();
                                isMPStopped = false;
                            }

                        }

                    } catch (Exception e) {

                    }
                } else {
                    toastGeneric("There is no song playing right now.");
                }
            }
        });

        //This will skip the song currently playing in our CDLL or throw an error.
        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (mp.isPlaying()) {
                        mp.reset();
                        // It will throw an error if it can't skip,
                        // so we need to make sure that the next node exists. :)
                        if(currentSong.next != null) {
                            currentSong = currentSong.next;

                        }
                        String pathToPlay = currentSong.song.getPath();
                        mp.setDataSource(pathToPlay);
                        mp.prepareAsync();
                    } else {
                        toastGeneric("There is no song playing right now.");
                    }

                } catch (Exception e) {
                    toastGeneric("There was an error RIP hope it wasn't during a presentation.");
                }
            }
        });
        // This will play the previous song. Who would've guessed?
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (mp.isPlaying()) {
                        mp.reset();
                        // Same as above, we need to make sure the previous node
                        // is not null.
                        if(currentSong.previous != null) {
                            currentSong = currentSong.previous;
                        }
                        String pathToPlay = currentSong.song.getPath();
                        mp.setDataSource(pathToPlay);
                        mp.prepareAsync();
                    } else {
                        toastGeneric("There is no song playing right now.");
                    }

                } catch (Exception e) {
                    toastGeneric("There was an error RIP hope it wasn't during a presentation.");
                }
            }
        });

        // Sets our button listener
        selectButton.setOnClickListener((v) -> {
            // Unicorn Picker is a third party library that allows a unified method of
            // selecting files and folders. The reason we decided to go with this method
            // is to preserve compatibility across our app with little issue.
            // In Android, due to their sweeping API changes, much code
            // that was written for API 30 won't work with 21-28.
            // Our solution to this is to use a third party library that is updated
            // to work with files across most API versions >21.
            UnicornFilePicker.from(MainActivity.this)
                    .addConfigBuilder()
                    .selectMultipleFiles(true)
                    .showOnlyDirectory(false)
                    .setRootDirectory(Environment.getExternalStorageDirectory().getAbsolutePath())
                    .showHiddenFiles(false)
                    .addItemDivider(true)
                    .setFilters(filters)
                    .theme(R.style.UnicornFilePicker_Dracula)
                    .build()
                    .forResult(Constants.REQ_UNICORN_FILE);
        });
    }


    // Here is our onActivityResult method. This is now separated out of the onCreate method because
    // it was causing issues. Basically This is overidden for the CLASS, not the method itself.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQ_UNICORN_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                // Two important arraylists for populating our JSON file.
                // And two we don't use at all.
                ArrayList<String> titles = new ArrayList<>();
                ArrayList<String> paths = new ArrayList<>();
                ArrayList<String> artists = new ArrayList<>();
                ArrayList<String> genres = new ArrayList<>();
                // This just gets all of the files that were returned from the previous activity.
                songs = data.getStringArrayListExtra("filePaths");
                // Counter. Unused Variable
                int counter = 0;
                // this loop iterates over EVERY SELECTED FILE. It will be used to
                // store our song's information in its respective JSON file.
                for (String file : songs) {
                    Log.e(TAG, file);
                    File realFile = new File(songs.get(counter));
                    // this adds the path to our paths list. ALSO VERY IMPORTANT.
                    paths.add(file);
                    // Does the song end in MP3? Gross, let's remove it.
                    String songName = realFile.getName().replace(".mp3", "");
                    // Adds the song's name to our titles list. VERY important.
                    titles.add(songName);
                    // These just add the necessary artist and genre fields so we can create our song objects.
                    artists.add("No Artist Assigned");
                    genres.add("No Genre Assigned");
                    Log.e("FILE NAME: ", songName);


                    // Unused Incremented Variable
                    counter++;
                }
                // END OF FOR LOOP


                // This is just setting the directory of the folder so we can see if it exists or not.
                // This is the name of the folder we've either created or need to create.
                String sdkunder29 = "/WGACA/songs.json";
                String sdkOver29 = "/Documents/songs.json";
                File directoryToCreate;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    directoryToCreate = new File(Environment.getExternalStorageDirectory(), sdkOver29);
                } else {
                    directoryToCreate = new File(Environment.getExternalStorageDirectory(), sdkunder29);
                }


                // Does the directory exist? If so, we will populate the existing file.
                Log.e("DIRECTORY: ", directoryToCreate.getPath());
                if (directoryToCreate.exists()) {
                    // Populates an existing Directory if it's necessary.
                    Log.e("DIRECTORY EXISTS?: ", "True");


                    // This array holds our entries BEFORE we repopulate our list
                    // with the new entries. This allows us to check for duplicates
                    // in the following nested for loop below. It's not the most optimized
                    // thing in the world, but it is relatively straightforward.
                   ArrayList<Song> getAllSongsBefore = parser.getEntries();
                    // This method populates our JSON file
                    parser.populateExistingList(paths, titles, artists, genres);


                    // This boolean will help us identify duplicates and keep them out
                    // of our circular doubly linked list.
                    boolean alreadyExists = false;


                    // Ensure that we add our newly selected songs to our CDLL.
                    // This code should also skip duplicates.
                    for (int i = 0; i < paths.size(); i++) {
                        for (int j = 0; j < getAllSongsBefore.size(); j++) {
                            // This inner loop checks our previous json file against the
                            // songs that were just selected and changes our boolean for us.
                            if (getAllSongsBefore.get(j).getPath().equals(paths.get(i))) {
                                alreadyExists = true;
                                Log.e("INNER LOOP: ", "SONG ALREADY EXISTS");
                            } else {
                                Log.e("INNER LOOP:", "SONG DOES NOT EXIST");
                            }

                        }
                    }

                    //    Log.e("CDLL POPULATED:",CDLList.head.song.getTitle());

                    // This code will parse our JSON file and reset the recycler view to include all of our added songs.
                    songArr = parser.getEntries();
                    recyclerView.setHasFixedSize(false);
                    recyclerView.setLayoutManager(new LinearLayoutManager(this));
                    Node tempNode;

                    tempNode = new Node(currentSong.song);
                    repopulateCDLL();
                    currentSong = CDLList.head;
                    while(!currentSong.song.getPath().equals(tempNode.song.getPath())) {
                        currentSong = currentSong.next;
                    }
                    adapter.updateList(songArr);



                    // Log.e("POPULATELISTERROR: ", "FOLDER ALREADY EXISTS.");
                } else {
                    // This executes the FIRST TIME our user runs the app and selects files.
                    try {
                        parser.populateListFirstTime(paths, titles, artists, genres);
                        Log.e("ITEMS: ", String.valueOf(parser.getLength()));
                        Log.e("POPULATELISTSUCCESS", "FILES ADDED TO JSON FILE SUCCESSFULLY (FIRST TIME RUN)");

                        genreOptions = parser.search(true, "genre");
                        artistOptions = parser.search(true,"artist");

                        // This will populate and initialize our recycleview our FIRST ENTRIES once they've been added.
                        songArr = parser.getEntries();
                        if (songArr != null) {
                            RecyclerView recyclerView = (RecyclerView) findViewById(R.id.songList);
                            recyclerView.setHasFixedSize(false);
                            recyclerView.setLayoutManager(new LinearLayoutManager(this));
                            adapter.updateList(songArr);
                            recyclerView.setAdapter(adapter);
                        } else {
                            // This prolly means something went wrong RIP :(
                            // You should NOT hit this else block, it's here for reference
                        }
                    } catch (IOException e) {
                        Log.e("ERROR IN IO", "IO ERROR IN POPULATE FIRST TIME");
                        Log.e(TAG, e.getMessage());
                    }
                    if (paths != null) {
                        // This is our for loop to add the new songs to our CDLL.
                        for (int i = 0; i < paths.size(); i++) {
                            CDLList.insertNode(new Song(titles.get(i), paths.get(i)));
                            Log.e("CDLL POPULATED:", CDLList.head.song.getTitle());
                        }
                        currentSong = CDLList.head;
                        String pathToPlay = currentSong.song.getPath();
                        try {
                            mp.setDataSource(pathToPlay);
                            mp.prepareAsync();
                            handler.postDelayed(timeRunnable,0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }

            } else {
                // Lets us know our path was RIP Empty. THIS SHOULD NOT REALLY HAPPEN UNLESS THE USER CLOSES THE APP
                Log.e("path", "empty");
            }
        }
// This is the same as our regular recyclerView code. This one just changes the array.
    // Currently does NOT work, everything needs to be changed to arraylists.
        public void resetSongsAfterSearch(ArrayList<Song> songArr) {
            RecyclerView recyclerView = findViewById(R.id.songList);
            Log.e("RV RESET", "RECYCLER VIEW RESET IN RESET SONGS AFTER SEARCH");
            adapter.updateList(songArr);
        }

// I love love love this little method. It allows me to basically throw any text to the screen
    // really fast.
        public void toastGeneric(String textToShow) {
        // IF a toast is on the screen, it won't throw an error lmao
        try{ toast.getView().isShown();
            // Sets the text of our currently shown toast.
                toast.setText(textToShow);
                // This is a doozy. If the above method throws an excpetion,
            // Then we will actually create the toast. THEN if we press the button again,
            // there won't be an exception until the toast disappears.
            } catch (Exception e) {
                toast = Toast.makeText(this, textToShow, Toast.LENGTH_SHORT);
            }
        // Gotta show that toast.
            toast.show();
        }
// This is the permission checker. It opens a popup that will ask the user for access to write to their storage.
       // The request code is declared at the top of our class. It can be any number :D
    // If the permissions aren't granted, the program will return a different number, I think
    // it's like -1 or something.
        public void permissionCheck(String permissionType, int reqCode) {
            if(ContextCompat.checkSelfPermission(MainActivity.this, permissionType) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[] {permissionType }, reqCode);
            } else {
               // toastGeneric("You're all set with your permission, thank you for trusting Circle Player");
            }
        }
// This method is like a return statement for our permission checker method. It let's the user know that we really need access.
    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(reqCode, permissions, grantResults);
        if (reqCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toastGeneric("You granted storage permission, thank you! <3");
            } else {
                toastGeneric("Why don't you trust us, this is a school project. :(");
            }
        }
    }

    public void openTutorial() {
        // Get the parameters so we can set the size of our popup window.
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        // Create a final version of our popup window.
        final PopupWindow editSongPopup = new PopupWindow(editsongView, width, height, true);
        // This makes the background opaque.
        editSongPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // Open the popup
        editSongPopup.showAtLocation(editsongView, Gravity.CENTER,0,0);
    }


    // Song Editor Popup method. Used on song item long click.
    public void openSongEditor(Song song) {
        // Get the parameters so we can set the size of our popup window.
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
    // Create a final version of our popup window.
        final PopupWindow editSongPopup = new PopupWindow(editsongView, width, height, true);
        // This makes the background opaque.
        editSongPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // Button and text area initializations
        Button saveBtn= (Button) editsongView.findViewById(R.id.saveChangesBtn);
        Button deleteBtn = (Button) editsongView.findViewById(R.id.delBtn);
        EditText editTitleBox =  editsongView.findViewById(R.id.editTitleBox);
        EditText editArtistBox =  editsongView.findViewById(R.id.editArtistBox);
        EditText editGenreBox =  editsongView.findViewById(R.id.editGenreBox);

        // Populate the text areas with our song info. Makes it easier to edit.
        editTitleBox.setText(song.getTitle());
        editArtistBox.setText(song.getArtist());
        editGenreBox.setText(song.getGenre());

        // Open the popup
        editSongPopup.showAtLocation(editsongView, Gravity.CENTER,0,0);


        genreSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if(!genreSpinnerInitialized) {
                    genreSpinnerInitialized = true;
                    return;
                }
                editGenreBox.setText(genreOptions.get(i));

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                editGenreBox.setText(song.getGenre());
            }
        });

        artistSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if(!artistSpinnerInitialized) {
                    artistSpinnerInitialized = true;
                    return;
                }
                editArtistBox.setText(artistOptions.get(i));

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                editArtistBox.setText(song.getArtist());
            }
        });

        genreSpinnerInitialized = false;
        artistSpinnerInitialized = false;

        // What happens when the delete button is pressed.
        deleteBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // These conditions are important because if the pointer for
                // the next node in current song is null, the program will crash.
                if(currentSong.next != null && currentSong != null) {
                    // Our parser class has a method for removing the
                    // song from the JSON file
                parser.deleteSong(song.getPath());
                // Reset the song arraylist
                songArr = parser.getEntries();
                // Resfresh the adapter for the recycler view
                adapter.updateList(songArr);
                // Temporary node that will help us reset our CDLL.
                Node tempNode;
                // Another important condition to check. The program will crash
                    // if currentSong.next is null.
                if(currentSong.next != null) {
                    tempNode = new Node(currentSong.next.song);
                    // This while loop traverses the CDLL to the pointer of the
                    // song after the one that was deleted.
                    while(!currentSong.song.getPath().equals(tempNode.song.getPath())) {
                        currentSong = currentSong.next;

                    }

                }
                // Here we need to repopulate the entire CDLL. This is because deleting a node
                    // requires resetting all of the pointers. That's what happens in our
                    // deleteAllNodes class. We refill the CDLL afterwards.
                    repopulateCDLL();
                    currentSong = CDLList.head;
                    String pathToPlay = currentSong.song.getPath();
                    // recyclable code for resetting the media player.
                    mp.reset();
                    try {
                        mp.setDataSource(pathToPlay);
                        mp.prepareAsync();
                        handler.removeCallbacks(timeRunnable);
                        handler.postDelayed(timeRunnable,0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    // If the playlist has no songs left, the CDLL is useless. Therefore we require
                    // the user to keep at least one song in the playlist.
                    toastGeneric("Playlist Must Have at Least One Song!");
                }
                // We assume the user is done editing the song if they delete it, so we close the popup.
                editSongPopup.dismiss();
            }
        });

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                genreSpinnerInitialized = false;
                artistSpinnerInitialized = false;

                // Below we need to get String variable of all of the data in the edit fields.
                String newTitle =  editTitleBox.getText().toString();
                String newArtist =  editArtistBox.getText().toString();
                String newArtistDropdown = artistSpinner.getSelectedItem().toString();
                String newGenre =  editGenreBox.getText().toString();



                // We require at least 3 characters in every field for the user to save the data.
                if(newTitle.length() >= 3 && newArtist.length() >= 3 && newGenre.length() >= 3) {
                    // Here we traverse and change the song that needs to be changed. We break if
                    // we find that song early for optimization purposes.
                    for (int i = 0; i < songArr.size(); i++) {
                        if (songArr.get(i).getPath().equals(song.getPath())) {
                            Log.e("save Changes1", songArr.get(i).getTitle() + " "
                                                            + songArr.get(i).getGenre());
                            songArr.get(i).setTitle(newTitle);
                            songArr.get(i).setArtist(newArtist);
                            songArr.get(i).setGenre(newGenre);
                            Log.e("save Changes2", songArr.get(i).getTitle() + " "
                                    + songArr.get(i).getGenre());
                            break;
                        }
                    }
                    // Try block because we are parsing and rewriting our file with
                    // the updated song's information.
                    try {
                        // Repopulate after change is the same as populate first time but
                        // with more specifications in how we save the data.
                        parser.repopulateAfterChange(songArr);
                        songArr = parser.getEntries();
                        // Update the list again.
                        adapter.updateList(songArr);
                        // Update current song updates the song in our CDLL.
                        updateCurrentSong(song,newArtist,newTitle,newGenre);
                        // May be able to remove this call. Untested
                        repopulateCDLL();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // We remove the popup after the user saves their changes
                    editSongPopup.dismiss();
                } else {
                    // This toast appears if you try to save changes with fields
                    // that have less than 3 characters.
                    toastGeneric("The fields need to be at least 3 characters.");
                }
            }
        });


    }

    public void repopulateCDLL() {
        CDLList.deleteAllNodes();
        for (Song song : songArr) {
            CDLList.insertNode(song);
        }
    }

    public void updateCurrentSong(Song song, String newArtist, String newTitle, String newGenre) {
        String tempPath = currentSong.song.getPath();
        while(!currentSong.song.getPath().equals(song.getPath())) {
            currentSong = currentSong.next;
        }
        currentSong.song.setArtist(newArtist);
        currentSong.song.setTitle(newTitle);
        currentSong.song.setGenre(newGenre);

        while(!currentSong.song.getPath().equals(tempPath)) {
            currentSong = currentSong.next;
        }
        songTitle.setText(currentSong.song.getTitle());
    }

    protected void onPause() {
        super.onPause();

        SharedPreferences sharedPreferences = getSharedPreferences("firstLaunch", MODE_PRIVATE);
        SharedPreferences.Editor editPreferences = sharedPreferences.edit();
        if(firstLaunch = true) {
            editPreferences.putBoolean("firstLaunch", false);
            editPreferences.apply();
        }
    }
}



