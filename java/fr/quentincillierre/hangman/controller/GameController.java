package fr.quentincillierre.hangman.controller;

import java.io.IOException;

import fr.quentincillierre.hangman.Category;
import fr.quentincillierre.hangman.Difficulty;
import fr.quentincillierre.hangman.model.HangmanModel;
import fr.quentincillierre.hangman.model.WordRepository;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class GameController {

    @FXML
    private BorderPane mainPane;

    @FXML
    private Label wordLabel;

    @FXML
    private Label resultLabel;

    @FXML
    private Label hintLabel;

    @FXML
    private Label difficultyLabel;

    @FXML
    private Label categoryLabel;

    @FXML
    private ImageView hangmanImageView;

    @FXML
    private GridPane keyboardGrid;

    @FXML
    private MediaView backgroundVideoView;
    // Once the video has looped the first time, subsequent repeats should start at 4s
    private boolean backgroundOffsetApplied = false;

    private HangmanModel model;
    // Optional clue associated with the current word (e.g. "fastest land animal")
    private String currentClue;

    private Difficulty difficulty;
    private Category currentCategory;

    public void start(Category category, Difficulty difficulty) {
        this.currentCategory = category;
        this.difficulty = difficulty;

        WordRepository wordRepository;
        switch (category) {
            case MUSIC:
                wordRepository = new WordRepository("/music.txt");
                break;
            case FOOD:
                wordRepository = new WordRepository("/food.txt");
                break;
            case COUNTRY:
                wordRepository = new WordRepository("/country.txt");
                break;
            default:
                throw new IllegalArgumentException("Unsupported category: " + category);
        }

        // Words in resources may include an optional clue separated by '|', e.g.
        // "CHEETAH|Fastest land animal". Parse and keep the clue for the UI.
        String raw = wordRepository.getRandomWord(difficulty);
        String wordOnly = raw;
        currentClue = null;
        if (raw != null && raw.contains("|")) {
            String[] parts = raw.split("\\|", 2);
            wordOnly = parts[0].trim();
            currentClue = parts[1].trim();
        }
        this.model = new HangmanModel(wordOnly);
        applyDifficultyTheme(difficulty);
        if (keyboardGrid != null) {
            keyboardGrid.setDisable(false);
        }
        generateKeyboard();
        refreshUI();
        loadBackgroundVideo();
        resultLabel.setOpacity(0);
        if (difficultyLabel != null) {
            difficultyLabel.setText(toDisplayDifficulty(difficulty));
        }
        if (categoryLabel != null) {
            categoryLabel.setText(toDisplayCategory(category));
        }
    }

    private String getHintText() {
        // Prefer an explicit clue if provided in the word resource.
        if (currentClue != null && !currentClue.isEmpty()) {
            return "Clue: " + currentClue;
        }

        if (model == null) {
            return "Clue: Choose a category and start playing.";
        }

        String word = model.getWordToGuess().replaceAll("[^A-Z]", "");
        if (word.isEmpty()) {
            return "Clue: No clue available.";
        }
        // Fallback: provide a simple descriptive hint (letters + first letter)
        char firstLetter = word.charAt(0);
        String base = String.format("%d letters, starts with '%s'", word.length(), firstLetter);
        if (difficulty == Difficulty.EASY) {
            return "Easy — " + base;
        } else if (difficulty == Difficulty.MEDIUM) {
            return "Medium — " + base;
        }
        return "Hard — " + base;
    }

    private void applyDifficultyTheme(Difficulty difficulty) {
        String accentColor;
        String resultColor;

        switch (difficulty) {
            case EASY:
                accentColor = "#4FD1C5";
                resultColor = "#BEE3F8";
                break;
            case MEDIUM:
                accentColor = "#F6AD55";
                resultColor = "#F7F2C1";
                break;
            default:
                accentColor = "#EF476F";
                resultColor = "#FFB3C1";
                break;
        }

        if (mainPane != null) {
            mainPane.setStyle("-fx-background-color: rgba(10, 20, 38, 0.68); -fx-background-radius: 20; -fx-border-color: " + accentColor + "; -fx-border-width: 1px; -fx-border-radius: 20;");
        }
        if (hintLabel != null) {
            hintLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-weight: 600;");
        }
        if (difficultyLabel != null) {
            difficultyLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-background-color: rgba(255,255,255,0.08); -fx-padding: 6 12; -fx-background-radius: 12; -fx-font-weight: bold;");
        }
        if (resultLabel != null) {
            resultLabel.setStyle("-fx-text-fill: " + resultColor + "; -fx-font-weight: bold;");
        }
    }

    /**
     * Loads a looping background video into the MediaView. Tries the
     * user-requested file `videobackground (2).mp4` then falls back to other
     * packaged videos if not found.
     */
    private void loadBackgroundVideo() {
        if (backgroundVideoView == null) {
            return;
        }

        try {
            java.net.URL mediaUrl = getClass().getResource("/videos/videobackground (2).mp4");
            if (mediaUrl == null) {
                mediaUrl = getClass().getResource("/videos/videobackground.mp4");
            }
            if (mediaUrl == null) {
                mediaUrl = getClass().getResource("/videos/backgroundvideo.mp4");
            }

            if (mediaUrl != null) {
                Media media = new Media(mediaUrl.toExternalForm());
                MediaPlayer player = new MediaPlayer(media);
                // We'll control looping manually so we can change the restart position
                player.setCycleCount(1);
                player.setMute(true);
                backgroundVideoView.setMediaPlayer(player);
                backgroundVideoView.setOpacity(0.95);
                backgroundVideoView.setPreserveRatio(false);

                // Start playback once media is ready
                player.setOnReady(() -> player.play());

                // When playback reaches the end, seek to 4s for subsequent plays and continue
                player.setOnEndOfMedia(() -> {
                    try {
                        // After the first loop, always start repeats at 4 seconds
                        backgroundOffsetApplied = true;
                        player.seek(Duration.seconds(4));
                        player.play();
                    } catch (Exception ex) {
                        System.err.println("Failed to loop background video: " + ex);
                    }
                });
            } else {
                System.err.println("Background video resource not found in /videos/");
                backgroundVideoView.setOpacity(0);
            }
        } catch (Exception e) {
            System.err.println("Failed to load background video: " + e);
            backgroundVideoView.setOpacity(0);
        }
    }

    public void goBackToMenu(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/quentincillierre/hangman/application/menu-view.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 721, 466);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
    }

    private void refreshUI() {
        if (model == null) {
            return;
        }

        wordLabel.setText(model.getHiddenWord());
        hintLabel.setText(getHintText());
        // Prefer realistic images if provided by the user in resources/pictures.
        String imageResource = findHangmanImageResource(model.getCurrentWrongs());
        if (imageResource != null) {
            hangmanImageView.setSmooth(true);
            hangmanImageView.setCache(true);
            hangmanImageView.setImage(new Image(getClass().getResource(imageResource).toExternalForm(), true));
        } else {
            // Fallback to existing stage images
            var res = getClass().getResource(String.format("/pictures/%s-hangman.png", model.getCurrentWrongs()));
            if (res != null) {
                hangmanImageView.setSmooth(true);
                hangmanImageView.setCache(true);
                hangmanImageView.setImage(new Image(res.toExternalForm(), true));
            } else {
                hangmanImageView.setImage(null);
            }
        }

        if (model.isLose() || model.isWin()) {
            keyboardGrid.setDisable(true);
            wordLabel.setText(model.getWordToGuess());
            resultLabel.setOpacity(1);
            resultLabel.setAlignment(Pos.CENTER);
            resultLabel.setText(model.isWin() ? "Victory !" : "Game Over !");
        }
    }

    private void generateKeyboard() {
        keyboardGrid.getChildren().clear();

        String accentColor;
        if (difficulty == Difficulty.MEDIUM) {
            accentColor = "#F6AD55";
        } else if (difficulty == Difficulty.HARD) {
            accentColor = "#EF476F";
        } else {
            accentColor = "#4FD1C5";
        }

        String defaultButtonStyle =
                "-fx-background-color: rgba(255,255,255,0.08);" +
                "-fx-text-fill: #F8FAFC;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-border-color: rgba(255,255,255,0.18);" +
                "-fx-border-width: 1px;" +
                "-fx-background-radius: 10;" +
                "-fx-border-radius: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 4, 0, 0, 2);";

        String hoverButtonStyle =
                "-fx-background-color: " + accentColor + ";" +
                "-fx-text-fill: #0B1120;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 10;";

        for (char c = 'A'; c <= 'Z'; c++) {
            Button letterButton = new Button(String.valueOf(c));
            letterButton.setPrefSize(40, 40);
            letterButton.setStyle(defaultButtonStyle);

            // Disable the button after use so the same letter can't be
            // guessed repeatedly (previously it stayed clickable forever).
            letterButton.setOnAction(event -> {
                handleKeyboardInput(letterButton.getText());
                letterButton.setDisable(true);
            });
            letterButton.setOnMouseEntered(event -> {
                if (!letterButton.isDisabled()) {
                    letterButton.setStyle(hoverButtonStyle);
                }
            });
            letterButton.setOnMouseExited(event -> {
                if (!letterButton.isDisabled()) {
                    letterButton.setStyle(defaultButtonStyle);
                }
            });

            int index = c - 'A';
            int col = index % 13;
            int row = index / 13;
            keyboardGrid.add(letterButton, col, row);
        }
    }

    public void handleKeyboardInput(String character) {
        if (model == null || model.isWin() || model.isLose()) {
            return;
        }

        if (character != null && character.length() == 1) {
            char letter = Character.toUpperCase(character.charAt(0));
            if (letter >= 'A' && letter <= 'Z') {
                model.tryLetter(letter);
                refreshUI();
            }
        }
    }

    @FXML
    public void restartGame() {
        if (currentCategory == null || difficulty == null) {
            return;
        }
        start(currentCategory, difficulty);
    }

    private String findHangmanImageResource(int stage) {
        String[] candidates = new String[] {
            String.format("/pictures/pic%d.jpeg", stage),
            String.format("/pictures/realistic-%d.png", stage),
            "/pictures/realistic-hangman.png",
            String.format("/pictures/%d-hangman.png", stage)
        };

        for (String c : candidates) {
            if (getClass().getResource(c) != null) {
                return c;
            }
        }
        return null;
    }

    private String toDisplayDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            return "UNKNOWN MODE";
        }
        String label = difficulty.name().toLowerCase();
        return label.substring(0, 1).toUpperCase() + label.substring(1) + " mode";
    }

    private String toDisplayCategory(Category category) {
        if (category == null) {
            return "UNKNOWN CATEGORY";
        }
        String label = category.name().toLowerCase();
        return "Category: " + label.substring(0, 1).toUpperCase() + label.substring(1);
    }
}