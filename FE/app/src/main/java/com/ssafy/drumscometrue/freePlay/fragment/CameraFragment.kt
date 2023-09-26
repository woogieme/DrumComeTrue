/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ssafy.drumscometrue.freePlay.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.minus
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
//import com.google.firebase.ml.vision.common.FirebaseVisionImage
//import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.ssafy.drumscometrue.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.ssafy.drumscometrue.databinding.FragmentCameraBinding
import com.ssafy.drumscometrue.freePlay.OverlayView
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max


/**
 * 카메라를 사용하여 실시간으로 사용자의 포즈를 감지하고 표시하는 데 사용되는 프래그먼트
 * TensorFlow와 CameraX라이브러리를 사용하여 구현
 */
class CameraFragment : Fragment() {

    /**
     * 클래스 내부에 정적인 변수와 메서드를 선언하는 데 사용
     * */
    companion object {
        private const val TAG = "Pose Landmarker"
    }

    //
    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private lateinit var displayMetrics: DisplayMetrics

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private val cameraFragment:CameraFragment = this


    //카메라 미리보기 및 이미지 분석과 관련된 변수들 -> glSurfaceView로 변경해도 될듯
    private var preview: Preview? = null    // 카메라에서 가져온 실시간 미리보기 화면을 표시하는 데 사용
    private var imageAnalyzer: ImageAnalysis? = null    //카메라로부터 가져온 이미지 프레임을 분석하고 처리하는 데 사용
    private var camera: Camera? = null
    //카메라 공급자 인스턴스를 저장하는 변수
    private var cameraProvider: ProcessCameraProvider? = null
    //현재 사용중인 카메라의 렌즈 방향 저장하는 함수
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService


    private lateinit var soundPool: SoundPool
    private val soundMap = mutableMapOf<String, Int>()

    private var start = false
    private var setFoot = false
    var setLeftKnee : Float = 1F
    var setRightKnee : Float = 1F
    private var leftHandEstimation = mutableMapOf<String, Boolean>(
        "crash" to false,
        "ride" to false,
        "hiHat" to false,
        "hTom" to false,
        "mTom" to false,
        "floorTom" to false,
        "snare" to false
    )
    private var rightHandEstimation = mutableMapOf<String, Boolean>(
        "crash" to false,
        "ride" to false,
        "hiHat" to false,
        "hTom" to false,
        "mTom" to false,
        "floorTom" to false,
        "snare" to false
    )
    private var leftHihat : Boolean = false
    private var rightBass : Boolean = false


    //ML_Kit테스트
    // 포즈 인식 클라이언트에 적용되는 옵션
    private val options by lazy {
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    }
    // 위에서 생성한 Options으로 생성한 PoseDetector Client
    private val poseDetector by lazy {
        PoseDetection.getClient(options)
    }

    // CameraX 영상을 분석 후 발견된 Landmark를 담고 있는 Pose객체 넘겨주는 콜백
    private val onPoseDetected: (pose: Pose) -> Unit = { pose ->
    }

    // ML Kit Pose Detector
    private class CameraAnalyzer(
        private val poseDetector: PoseDetector,
        private val onPoseDetected: (pose: Pose) -> Unit,
        private val cameraFragment: CameraFragment
    ) : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image ?: return //이미지 없으면 중단

//            val imageRotation = degreesToFirebaseRotation(imageProxy.imageInfo.rotationDegrees)
//            // ML_Kit에 전달할 입력 이미지 설정, 회전정보 함께 전달
//            val image = FirebaseVisionImage.fromMediaImage(mediaImage, imageRotation)

            // Firebase ML Kit Vision API의 FirebaseVisionImage를 InputImage로 변환
//            val inputImage = InputImage.fromBitmap(
//                image.bitmap,
//                imageProxy.imageInfo.rotationDegrees
//            )

            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // 포즈 감지기를 사용하여 입력 이미지를 처리, 포즈 감지 -> 비동기적 수행
            poseDetector.process(inputImage)
                //포즈감지 성공시 감지된 포즈 onPoseDetected콜백함수에 전달
                .addOnSuccessListener { pose ->
                    onPoseDetected(pose)
                    cameraFragment.poseResults(pose,inputImage)
                }
                .addOnFailureListener { e ->
                    //handel error
                    println("error")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                    mediaImage.close()
                }
        }
    }


    /**
     * pose의 위치에 맞춰 drum소리 play
     * */
    fun poseResults(
        pose: Pose,
        image: InputImage
    ){
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // fragmentCameraBinding.overlay - 화면에 그리기 작업을 처리하는 커스텀 OverlayView
                fragmentCameraBinding.overlay.setResults(
                    pose,
                    imageHeight = image.height,
                    imageWidth = image.width
                )
                if(!pose.allPoseLandmarks.isEmpty()){
                    val leftHand = pose.getPoseLandmark(19)
                    val rightHand = pose.getPoseLandmark(20)
                    val leftKnee = pose.getPoseLandmark(25)
                    val rightKnee = pose.getPoseLandmark(26)
                    val leftFoot = pose.getPoseLandmark(31)
                    val rightFoot = pose.getPoseLandmark(32)
                    val width = image.width
                    val height = image.height

                    if(!start){
                        val handler = Handler()

                        handler.postDelayed({
                            // 3초 후에 실행할 코드를 여기에 작성합니다.
                            settingEstimation(leftHand, height, width)
                            settingEstimation(rightHand, height, width)
                            setLeftKnee = leftKnee.position.y
                            setRightKnee  = rightKnee.position.y
                            println("초기셋팅"+setRightKnee/width)
                            start = true
                        }, 2000)
                    }else{
                        leftHandEstimation = cameraFragment.hit(leftHand, cameraFragment.leftHandEstimation, height, width)
                        rightHandEstimation = cameraFragment.hit(rightHand, cameraFragment.rightHandEstimation, height, width)
                        leftHandEstimation = cameraFragment.back(leftHand, cameraFragment.leftHandEstimation, height, width)
                        rightHandEstimation = cameraFragment.back(rightHand, cameraFragment.rightHandEstimation, height, width)

//                        hitLeftHihat(leftFoot, height, width)
//                        hitRightBass(rightFoot, height, width)
//                        backLeftHihat(leftFoot, height, width)
//                        backRightBass(rightFoot, height, width)
                        hitLeftHihat2(leftKnee,setLeftKnee, height, width)
                        hitRightBass2(rightKnee,setRightKnee, height, width)
                        backLeftHihat2(leftKnee,setLeftKnee, height, width)
                        backRightBass2(rightKnee,setRightKnee,  height, width)
                    }
                }
                // overlayView를 화면에 다시 그리도록 invalidate메서드 호출
                // -> 포즈 감지 결과가 화면에 업데이트 및 표시됨
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }


    /**
     * Fragment가 화면에 나타날 때 호출
     * 필요 권한 확인
     * PoseLandmarkerHelper를 다시 시작
     * */
    override fun onResume() {
        super.onResume()
        // 앱의 권한확인
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(   //Jetpack Navigation라이브러리 : 네비게이션 그래프에서 검색, 가져오는데 사용
                requireActivity(), R.id.find_id_ui_fragment  //requireActivity : 현재 속한 컨텍스트, R.id.fragment_container: 네비게이션 그래프에서 화면 간 전환을 관리하는 호스트 컨테이너
            ).navigate(R.id.action_camera_to_permissions)
        }
    }

    /**
     * Fragment의 뷰가 파괴될 때 호출
     * 백그라운드 스레드를 종료
     * */
    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        println("onCreate")
        setSound()
    }


    /**
     * Fragment의 뷰를 생성하고 해당 뷰를 반환
     * */
    override fun onCreateView(
        inflater: LayoutInflater,   // xml레이아웃 파일을 fragment의 뷰ㅜ로 생성하는 데사용
        container: ViewGroup?,  // fragment가 연결될 부모 뷰 그룹을 나타냄, 일반적으로 Fragment는 Activity의 레이아웃 내에서 특정 위치에 추가됨 이때 container는 해당위치를 가리킴
        savedInstanceState: Bundle? //fragment의 상태 저장 및 복원하는데 사용
    ): View {
        val context = requireContext()
        displayMetrics = context.resources.displayMetrics
        //FragmentCameraBinding클래스를 사용하여 뷰와 데이터 바인딩을 생성
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)
        // fragment의 실제 뷰
        return fragmentCameraBinding.root
    }


    /**
     * 뷰가 생성된 후 호출
     * 백그라운드 스레드를 초기화
     * PoseLandmarkerHelper를 생성
     * */
    @SuppressLint("MissingPermission") //Android앱의 어노테이션 Lint도구에 의해 감지되는 경고 무시하도록
    //권한 검사 없이 권한이 필요한 작업을 수행하는 코드
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }
    }


    /**
     * 카메라 초기화
     * 미리보기와 이미지 분석 설정
     * */
    private fun setUpCamera() {
        // ProcessCameraProvider인스턴스를 가져오는 비동기 작업 시작 -> 카메라 프로바이더 얻을 수 있음
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())//requireContext()현재 Fragment의 컨텍스트를 가져옴
        // 위의 작업 종료 시 콜백 호출
        // 카메라 프로바이더 구성, 카메라 사용 사례를 바인딩하는 역할
        cameraProviderFuture.addListener(//addListener : 비동기 작업
            {
                // cameraProviderFuture에서 얻은 CameraProvider를 cameraProvider에 할당
                cameraProvider = cameraProviderFuture.get()

                // 카메라 사용 사례를 설정
                // 카메라 미리보기, 이미지 캡쳐 및 다른 사용 사례를 설정하고 연결하는 작업을 수행
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
            //콜백을 메인 스레드에서 실행하기 위해 ContextCompat를 사용하여 메인 Executor가져옴
            //Android에서 UI업데이트 및 UI관련 작업은 주로 메인 스레드에서 처리되기때문
        )
    }



    /**
     * 카메라 사용 사례 설정
     * 뷰파인더에 미리보기 바인딩
     * */
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // 카메라 선택기를 설정
        // requireLensFacing(cameraFacing)을 사용하여 카메라 렌즈 방향을 설정, build로 선택기 생성
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // 미리보기 설정
        // 비율, 디스플레이의 회전 방향을 설정
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // 이미지 분석 사용 설정
        // 카메라에서 스트리밍되는 영상에서 이미지 분석을 수행하는 부분
        imageAnalyzer = //이미지 분석기 설정
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)    //분석기가 사용할 이미지의 종횡비 설정
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)   //이미지의 회전 방향 설정
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)   //이미지 처리 속도가 분석보다 빠를경우 최신 이미지만 유지
                .build()    // 설정한 내용으로 이미지 분석기 생성
                // 이미지 분석기의 설정 마무리 : 이미지 객체에 대한 설정작업을 진행하는 클로저 실행
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->   // 이미지 분석기에 분석할 작업 설정 -> 이미지 분석기가 카메라에서 받아온 이미지를 처리하는 부분
                        CameraAnalyzer(poseDetector, onPoseDetected, cameraFragment).analyze(image)
                    }
                }

        // 이전에 설정된 카메라 사용 사례 해제
        cameraProvider.unbindAll()

        //카메라 사용 사례 바인딩
        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // 미리보기를 viewFinder에 연결
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }











    /** SoundPool설정 */
    private fun setSound(){
        val context = context
        if(context != null) {
            // SoundPool 초기화
            // AudioAttributes는 오디오 재생에 대한 속성 정의하는데 사용
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) //미디어 재생을 위한 것, 오디오 시스템에 어떤 유형의 오디오 스트림을 사용할지 알려줌
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // 효과음 또는 알림음과 같은 음향효과를 나타내는 것을 나타냄
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(100) // 최대 동시 재생 스트림 수 (조절 가능)
                .setAudioAttributes(audioAttributes)
                .build()

            // 사운드 파일 미리 로드
            soundMap["bass"] = soundPool.load(context, R.raw.bass2, 1)
            soundMap["snare"] = soundPool.load(context, R.raw.snare, 1)
            soundMap["openHat"] = soundPool.load(context, R.raw.open_hat, 1)
            soundMap["closedHat"] = soundPool.load(context, R.raw.closed_hat, 1)
            soundMap["pedalHat"] = soundPool.load(context, R.raw.pedal_hat, 1)
            soundMap["crash"] = soundPool.load(context, R.raw.crash, 1)
            soundMap["ride"] = soundPool.load(context, R.raw.ride, 1)
            soundMap["hTom"] = soundPool.load(context, R.raw.high_tom, 1)
            soundMap["mTom"] = soundPool.load(context, R.raw.mid_tom, 1)
            soundMap["floorTom"] = soundPool.load(context, R.raw.floor_tom, 1)
        }
    }

    /** 처음 손의 위치 setting */
    private fun settingEstimation(landmarkList : PoseLandmark, width : Int, height : Int) : MutableMap<String, Boolean>{
        //px -> dp비율로 변환하기
        val position_x = landmarkList.position.x / width
        val position_y = landmarkList.position.y / height

        val updates = mutableMapOf(
            "crash" to false,
            "ride" to false,
            "hiHat" to false,
            "hTom" to false,
            "mTom" to false,
            "floorTom" to false,
            "snare" to false
        )

        if(position_y > 0.3){
            updates["crash"] = true
        }else if(position_y > 0.4){
            updates["crash"] = true
            updates["ride"] = true
            updates["hiHat"] = true
            updates["hTom"] = true
            updates["mTom"] = true
        }else if(position_y > 0.5){
            updates["crash"] = true
            updates["ride"] = true
            updates["hiHat"] = true
            updates["hTom"] = true
            updates["mTom"] = true
            updates["floorTom"] = true
            updates["snare"] = true
        }

        return updates
    }

    /** 처음 왼발(Hihat)의 위치 setting */
    private fun settingLeftHihat(leftFoot : PoseLandmark, width : Int, height : Int){
        //px -> dp비율로 변환하기
        val position_x = leftFoot.position.x / width
        val position_y = leftFoot.position.y / height
        if(position_y > 0.97)
            leftHihat = true
    }

    /** 처음 오른발(Bass)의 위치 setting */
    private fun settingRightBass(rightFoot : PoseLandmark, width : Int, height : Int){
        //px -> dp비율로 변환하기
        val position_x = rightFoot.position.x / width
        val position_y = rightFoot.position.y / height

        if(position_y > 0.97)
            rightBass = true
    }

    /** 처음 왼발(Hihat)의 위치 setting */
    private fun settingLeftHihat2(leftFoot : PoseLandmark, width : Int, height : Int){
        //px -> dp비율로 변환하기
        val position_x = leftFoot.position.x / width
        val position_y = leftFoot.position.y / height
        if(position_y > 0.97)
            leftHihat = true
    }

    /** 처음 오른발(Bass)의 위치 setting */
    private fun settingRightBass2(rightFoot : PoseLandmark, width : Int, height : Int){
        //px -> dp비율로 변환하기
        val position_x = rightFoot.position.x / width
        val position_y = rightFoot.position.y / height

        if(position_y > 0.97)
            rightBass = true
    }



    /** hit판단 */
    private fun hit(landmarkList : PoseLandmark, hitEstimation : MutableMap<String, Boolean>, width : Int, height : Int) : MutableMap<String, Boolean>{
        //px -> 비율로 변환하기
        val position_x = landmarkList.position.x / width
        val position_y = landmarkList.position.y / height

        if(position_y > 0.25) {
            if(hitEstimation["crash"] == false && position_x > 0.65){
                Log.d("Crash","Crash Hit")
                // 사운드 재생
                val soundId = soundMap["crash"]
                soundId?.let {
                    soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
            if(hitEstimation["ride"] == false && position_x < 0.2){
                Log.d("ride Hit","ride Hit")
                // 사운드 재생
                val soundId = soundMap["ride"]
                soundId?.let {
                    soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
            hitEstimation["crash"] = true
            hitEstimation["ride"] = true
        }

        if(position_y > 0.32) {
            if (hitEstimation["hTom"] == false && position_x > 0.3 && position_x < 0.6) {
                Log.d("hTom Hit", "hTom Hit")
                // 사운드 재생
                val soundId = soundMap["hTom"]
                soundId?.let {
                    soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
            if (hitEstimation["mTom"] == false && position_x > 0.15 && position_x < 0.3) {
                Log.d("mTom Hit", "mTom Hit")
                // 사운드 재생
                val soundId = soundMap["mTom"]
                soundId?.let {
                    soundPool.play(it, 0.8f, 0.7f, 1, 0, 1.0f)
                }
            }
            hitEstimation["hTom"] = true
            hitEstimation["mTom"] = true
        }

        if(position_y > 0.34) {
            if(hitEstimation["hiHat"] == false && position_x > 0.7){
//                println(leftHihat)
                if(leftHihat){
                    Log.d("closedHat Hit","closedHat Hit")
                    // 사운드 재생
                    val soundId = soundMap["closedHat"]
                    soundId?.let {
                        soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                    }
                }else{
                    Log.d("openHat Hit","openHat Hit")
                    // 사운드 재생
                    val soundId = soundMap["openHat"]
                    soundId?.let {
                        soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                    }
                }
            }
            hitEstimation["hiHat"] = true
        }
        if(position_y > 0.48){
            if(hitEstimation["snare"] == false && position_x > 0.5 && position_x < 0.9){
                Log.d("snare Hit","snare Hit")
                println(position_x)
                println(position_y)
                // 사운드 재생
                val soundId = soundMap["snare"]
                soundId?.let {
                    soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
            if(hitEstimation["floorTom"] == false && position_x < 0.3){
                Log.d("floorTom Hit","floorTom Hit")
                // 사운드 재생
                val soundId = soundMap["floorTom"]
                soundId?.let {
                    soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
            hitEstimation["snare"] = true
            hitEstimation["floorTom"] = true
        }

        return hitEstimation
    }

    private fun hitLeftHihat(leftFoot : PoseLandmark, width : Int, height : Int){
        val position_x = leftFoot.position.x / width
        val position_y = leftFoot.position.y / height

        if(leftHihat == false && position_y > 0.95 && position_x > 0.7){

            Log.d("[Foot] pedalHat hit!","[Foot] pedalHat hit! ${position_y}")
            val soundId = soundMap["pedalHat"]

            soundId?.let {
                soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
            }
            leftHihat = true
        }
    }
    private fun hitRightBass(rightFoot : PoseLandmark, width : Int, height : Int){
        val position_x = rightFoot.position.x / width
        val position_y = rightFoot.position.y / height
        if(rightBass == false && position_y > 0.95 && position_x > 0.2 && position_x < 0.5){

            Log.d("[Foot] bass hit!","[Foot] bass hit! ${position_y}")
            val soundId = soundMap["bass"]
            soundId?.let {
                soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
            }
            rightBass = true
        }
    }

    private fun hitLeftHihat2(leftFoot : PoseLandmark, setLeftKnee:Float, width : Int, height : Int){
        val position_x = leftFoot.position.x / width
        val position_y = leftFoot.position.y / height

        if(leftHihat == false && position_y < setLeftKnee / height - 0.03){

            Log.d("[Foot] pedalHat hit!","[Foot] pedalHat hit! ${position_y}")
            val soundId = soundMap["pedalHat"]
            val soundIdOpen = soundMap["openHat"]

            soundId?.let {
                soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
                //openHat소리 재생중이었으면 소리 끔
                soundIdOpen?.let{
                    soundPool.stop(soundIdOpen)
                }
            }
            leftHihat = true
        }
    }
    private fun hitRightBass2(rightFoot : PoseLandmark, setRightKnee:Float, width : Int, height : Int){
        val position_x = rightFoot.position.x / width
        val position_y = rightFoot.position.y / height
        if(rightBass == false && position_y < setRightKnee/height-0.03){

            Log.d("[Foot] bass hit!","[Foot] bass hit! ${position_y}")
            val soundId = soundMap["bass"]
            soundId?.let {
                soundPool.play(it, 1.0f, 1.0f, 1, 0, 1.0f)
            }
            rightBass = true
        }
    }


    /** hit소리를 낼 준비하는지 판단 */
    private fun back(landmarkList : PoseLandmark, hitEstimation : MutableMap<String, Boolean>, width : Int, height : Int) : MutableMap<String, Boolean>{
        //px -> dp비율로 변환하기
        val position_x = landmarkList.position.x / width
        val position_y = landmarkList.position.y / height

        if(position_y < 0.25) {
            hitEstimation["crash"] = false
            hitEstimation["ride"] = false
            hitEstimation["hiHat"] = false
            hitEstimation["hTom"] = false
            hitEstimation["mTom"] = false
            hitEstimation["floorTom"] = false
            hitEstimation["snare"] = false
        }
        if(position_y < 0.31) {
            hitEstimation["hiHat"] = false
            hitEstimation["hTom"] = false
            hitEstimation["mTom"] = false
            hitEstimation["floorTom"] = false
            hitEstimation["snare"] = false
        }
        if(position_y < 0.33) {
            hitEstimation["hiHat"] = false
            hitEstimation["floorTom"] = false
            hitEstimation["snare"] = false
        }
        if(position_y < 0.46){
            hitEstimation["floorTom"] = false
            hitEstimation["snare"] = false
        }

        return hitEstimation
    }
    private fun backLeftHihat(leftFoot : PoseLandmark, width : Int, height : Int){
        val position_x = leftFoot.position.x / width
        val position_y = leftFoot.position.y / height
        if(position_y < 0.93){
            leftHihat = false
        }
    }
    private fun backRightBass(rightFoot : PoseLandmark, width : Int, height : Int){
        val position_x = rightFoot.position.x / width
        val position_y = rightFoot.position.y / height
        if(position_y < 0.93){
            rightBass = false
        }
    }

    private fun backLeftHihat2(leftFoot : PoseLandmark, setLeftKnee:Float, width : Int, height : Int){
        val position_x = leftFoot.position.x / width
        val position_y = leftFoot.position.y / height
        if(position_y > setLeftKnee / height-0.01){
            leftHihat = false
        }
    }
    private fun backRightBass2(rightFoot : PoseLandmark, setRightKnee:Float, width : Int, height : Int){
        val position_x = rightFoot.position.x / width
        val position_y = rightFoot.position.y / height
        if(position_y > setRightKnee / height-0.01){
            rightBass = false
        }
    }

}
