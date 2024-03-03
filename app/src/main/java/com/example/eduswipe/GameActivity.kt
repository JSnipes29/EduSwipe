package com.example.eduswipe

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.eduswipe.adapters.QuestionCardAdapter
import com.example.eduswipe.databinding.ActivityGameBinding
import com.example.eduswipe.models.Question
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.Direction
import com.yuyakaido.android.cardstackview.StackFrom
import com.yuyakaido.android.cardstackview.SwipeableMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity that holds the game logic for playing against another player
 */
class GameActivity : AppCompatActivity(), CardStackListener {

    private lateinit var binding: ActivityGameBinding
    private val manager by lazy { CardStackLayoutManager(this, this) }
    private lateinit var db: FirebaseFirestore
    var listenState = false
    var firstListenState = false
    val gameLimit = 7
    val user = generateRandomString(7)
    val game = "game1"
    var gameState = 0
    var score = 0
    var questions = ArrayList<Question>()
    var answer = ""
    var opt = ""
    var opt1 = ""
    var opt2 = ""
    var opt3 = ""
    var opt4 = ""
    var startListener : ListenerRegistration? = null
    var gameListener : ListenerRegistration? = null
    var player = 0
    var q = ""



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = Firebase.firestore
        initializeGame()
        setupCard()
    }

    /**
     * Setups the start of a game
     */
    fun initializeGame() {
        val docRef = db.collection("game").document(game)
        docRef.get().addOnSuccessListener { document ->
            val user1 = document.get("user1")
            if (user1 != null) {
                player = 2
                docRef.update("user2", user).addOnSuccessListener {
                    q = document.getString("question")!!
                    playGame()
                }
            } else {
                player = 1
                binding.loadingAnim.visibility = View.VISIBLE
                binding.tvWait.visibility = View.VISIBLE
                q = nextQuestion()
                docRef.update(mapOf("user1" to user, "question" to q)).addOnSuccessListener {

                    startListener = docRef.addSnapshotListener { snapshot, e ->

                        if (!firstListenState) {
                            firstListenState = true
                            return@addSnapshotListener
                        }
                        if (snapshot?.getString("user2") != null) {
                            playGame()
                            return@addSnapshotListener
                        }
                    }
                }
            }
        }
    }

    /**
     * Setup the swiping animation and physics for swiping cards
     */
    fun setupCard() {
        manager.setStackFrom(StackFrom.None)
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL)
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(true)
        manager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual)
        manager.setOverlayInterpolator(LinearInterpolator())
        binding.csvQuestions.layoutManager = manager
        val cslm = binding.csvQuestions.layoutManager as (CardStackLayoutManager)
        cslm.setDirections(Direction.FREEDOM)
        binding.csvQuestions.adapter = QuestionCardAdapter(questions)
    }

    /**
     * Start the game loop
     */
    fun playGame() {
        startListener?.remove()
        binding.loadingAnim.visibility = View.GONE
        binding.tvWait.visibility = View.GONE
        val docRef = db.collection("game").document(game)
        gameListener = docRef.addSnapshotListener { snapshot, e ->
            if (!listenState) {
                listenState = true
                return@addSnapshotListener
            }
            if (e != null) {
                Log.e("TAG", "Listen failed.", e)
                return@addSnapshotListener
            }

            Log.d("TAG", "Current data: ${snapshot?.data}")

            if (binding.csvQuestions.adapter?.itemCount != 0) {
                questions.clear()
                binding.csvQuestions.adapter?.notifyDataSetChanged()
            }

            gameState = snapshot?.getLong("gameState")?.toInt() ?: 0
            val answerer = snapshot?.getString("answerer")
            if (answerer == user) {
                binding.tvMessage.visibility = View.VISIBLE
                if (opt == answer) {
                    binding.tvMessage.text = "+10 Points"
                    score += 10
                } else {
                    binding.tvSubmessage.visibility = View.VISIBLE
                    binding.tvMessage.text = "-10 Points"
                    binding.tvSubmessage.text = "The correct answer was $answer"
                    score -= 10
                }
            } else {
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvSubmessage.visibility = View.VISIBLE
                if (snapshot?.getString("answer") == answer) {
                    binding.tvMessage.text = "Opponent answered correctly"
                    binding.tvSubmessage.text = "The correct answer was $answer"
                } else {
                    binding.tvMessage.text = "Opponent answered incorrectly"
                    binding.tvSubmessage.text = "The correct answer was $answer"
                }
                binding.tvMessage.text = "Opponent answered"
                binding.tvSubmessage.text = "The correct answer was $answer"
            }


            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                binding.tvMessage.visibility = View.GONE
                binding.tvSubmessage.visibility = View.GONE
                getQuestion(snapshot?.getString("question")!!)
            }


            if (gameState > gameLimit) {
                //@TODO Show winner and score
                endGame()
                return@addSnapshotListener
            }
        }

        getQuestion(q)
    }

    /**
     * Get a random question id
     */
    fun nextQuestion() : String {
        return "q${(1..24).random()}"
    }

    /**
     * End the game, displaying the results
     */
    fun endGame() {
        gameListener?.remove()
        binding.clGame.visibility = View.GONE
        binding.clEndGame.visibility = View.VISIBLE
        val docRef = db.collection("game").document(game)
        val userScoreKey = if (player == 1) "user1Score" else "user2Score"
        docRef.update(mapOf("user1" to FieldValue.delete(), "user2" to FieldValue.delete(), "gameState" to 0, userScoreKey to score))

        CoroutineScope(Dispatchers.Main).launch {
            delay(300)
            docRef.get().addOnSuccessListener { document ->
                var oppScore = document.getLong(if (player == 1) "user2Score" else "user1Score")
                if (oppScore == null) {
                    oppScore = 0
                }
                binding.tvScore.text = "${score}pts"
                binding.tvScoreOpp.text="${oppScore}pts"
                if (score > oppScore!!) {
                    binding.tvEndMessage.text = "You Win!"
                } else if (score < oppScore!!) {
                    binding.tvEndMessage.text = "You Lose!"
                } else {
                    binding.tvEndMessage.text = "Draw!"
                }
            }
        }
    }

    /**
     * Get the specified question from firebase
     *
     * @param question The question id of the question to get
     */
    fun getQuestion(question: String) {
        val docRef = db.collection("questions").document(question)
        docRef.get().addOnSuccessListener { document ->
            opt1 = document.getString("option1")!!
            opt2 = document.getString("option2")!!
            opt3 = document.getString("option3")!!
            opt4 = document.getString("option4")!!
            binding.tvTopAnswer.text = opt1
            binding.tvBottomAnswer.text = opt2
            binding.tvLeftAnswer.text = opt3
            binding.tvRightAnswer.text = opt4
            answer = document.getString("answer")!!
            val q = Question(document.getString("question")!!, answer)
            questions.add(q)
            binding.csvQuestions.adapter?.notifyItemInserted(0)

        }

    }

    override fun onCardDragging(direction: Direction, ratio: Float) {
        Log.d("CardStackView", "onCardDragging: d = ${direction.name}, r = $ratio")
    }

    override fun onCardSwiped(direction: Direction) {
        gameState++
        opt = when(direction) {
            Direction.Top -> opt1
            Direction.Bottom -> opt2
            Direction.Left -> opt3
            Direction.Right -> opt4
            else -> ""
        }
        val nextQuestion = nextQuestion()
        db.collection("game").document(game).update(mapOf("answerer" to user, "gameState" to gameState, "question" to nextQuestion, "answer" to opt))
        Log.d("CardStackView", "onCardSwiped: p = ${manager.topPosition}, d = $direction")
    }

    override fun onCardRewound() {
        Log.d("CardStackView", "onCardRewound: ${manager.topPosition}")
    }

    override fun onCardCanceled() {
        Log.d("CardStackView", "onCardCanceled: ${manager.topPosition}")
    }

    override fun onCardAppeared(view: View, position: Int) {
        Log.d("CardStackView", "onCardAppeared: ($position)")
    }

    override fun onCardDisappeared(view: View, position: Int) {
        Log.d("CardStackView", "onCardDisappeared: ($position)")
    }


    companion object {
        /**
         * Generates a random string of a specified length
         * @param length length of outputed string
         *
         * @return random string
         */
        fun generateRandomString(length: Int): String {
            val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..length)
                .map { charset.random() }
                .joinToString("")
        }
    }
}