package com.mealchak.mealchakserverapplication.service;

import com.mealchak.mealchakserverapplication.dto.request.SignupRequestDto;
import com.mealchak.mealchakserverapplication.dto.request.UserUpdateDto;
import com.mealchak.mealchakserverapplication.dto.response.HeaderDto;
import com.mealchak.mealchakserverapplication.jwt.JwtTokenProvider;
import com.mealchak.mealchakserverapplication.model.Location;
import com.mealchak.mealchakserverapplication.model.User;
import com.mealchak.mealchakserverapplication.oauth2.KakaoOAuth2;
import com.mealchak.mealchakserverapplication.oauth2.UserDetailsImpl;
import com.mealchak.mealchakserverapplication.oauth2.provider.KakaoUserInfo;
import com.mealchak.mealchakserverapplication.repository.UserInfoRepository;
import com.mealchak.mealchakserverapplication.repository.UserRepository;
import com.mealchak.mealchakserverapplication.repository.mapping.UserInfoMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserInfoRepository userInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final KakaoOAuth2 kakaoOAuth2;
    private final AuthenticationManager authenticationManager;
    private static final String Pass_Salt = "AAABnv/xRVklrnYxKZ0aHgTBcXukeZygoC";

    @Autowired
    public UserService(JwtTokenProvider jwtTokenProvider,UserInfoRepository userInfoRepository,UserRepository userRepository, PasswordEncoder passwordEncoder, KakaoOAuth2 kakaoOAuth2, AuthenticationManager authenticationManager) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userInfoRepository = userInfoRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.kakaoOAuth2 = kakaoOAuth2;
        this.authenticationManager = authenticationManager;
    }

    public User getUser(String email) {
        User member = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입되지않은 아이디입니다."));
        return member;
    }


    public HeaderDto kakaoLogin(String authorizedCode) {
        // 카카오 OAuth2 를 통해 카카오 사용자 정보 조회
        KakaoUserInfo userInfo = kakaoOAuth2.getUserInfo(authorizedCode);
        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();
        String email = userInfo.getEmail();
        String thumbnailImg = userInfo.getThumbnailImg();
        String profileImg = userInfo.getProfileImg();
        String address = "강남구";
        double latitude = 37.497910;
        double longitutde = 127.027678;
        Location location = new Location(address, latitude, longitutde);

        // 우리 DB 에서 회원 Id 와 패스워드
        // 회원 Id = 카카오 nickname
        String username = nickname;
        // 패스워드 = 카카오 Id + ADMIN TOKEN
        String password = kakaoId + Pass_Salt;

        // DB 에 중복된 Kakao Id 가 있는지 확인
        User kakaoUser = userRepository.findByKakaoId(kakaoId)
                .orElse(null);

        // 카카오 정보로 회원가입
        if (kakaoUser == null) {
            // 패스워드 인코딩
            String encodedPassword = passwordEncoder.encode(password);

            kakaoUser = new User(kakaoId, nickname, encodedPassword, email, thumbnailImg, profileImg, location);
            userRepository.save(kakaoUser);
        }

        // 로그인 처리
        Authentication kakaoUsernamePassword = new UsernamePasswordAuthenticationToken(email, password);
        Authentication authentication = authenticationManager.authenticate(kakaoUsernamePassword);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        HeaderDto headerDto = new HeaderDto();
        User member = userRepository.findByKakaoId(kakaoId).orElse(null);
        headerDto.setTOKEN(jwtTokenProvider.createToken(email, member.getId(), nickname));
        return headerDto;
    }

    @Transactional
    public void registerUser(SignupRequestDto requestDto) {
        String username = requestDto.getUsername();
        String password;
        password = passwordEncoder.encode(requestDto.getPassword());
        User user = new User(username, password);
        userRepository.save(user);
    }

    @Transactional
    public String updateUsername(User oldUser, String newUsername, UserDetailsImpl userDetails) {
        if (userDetails != null) {
            User user = userRepository.findById(oldUser.getId()).orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));
            user.updateUsername(newUsername);
            return user.getUsername();
        } else {
            throw new IllegalArgumentException("로그인된 유저가 아닙니다.");
        }
    }

    // 유저 위치 저장
    @Transactional
    public Location updateUserLocation(UserUpdateDto updateDto, User user) {
        User user1 = userRepository.findById(user.getId()).orElseThrow(()->new IllegalArgumentException("해당 유저가 존재하지 않습니다."));
        Location location = new Location(updateDto);
        user1.updateUserDisc(location);
        return user1.getLocation();
    }

    //유저정보
    @Transactional
    public UserInfoMapping userInfo(UserDetailsImpl userDetails){
        if (userDetails != null) {
            return userInfoRepository.findByEmail(userDetails.getUser().getEmail()).orElseThrow(() -> new IllegalArgumentException("회원이 아닙니다."));
        } else {
            throw new IllegalArgumentException("로그인 하지 않았습니다.");
        }
    }

    @Transactional
    public String login(SignupRequestDto requestDto){
        User user = userRepository.findByUsername(requestDto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 유저입니다."));
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("잘못된 비밀번호입니다.");
        }
        return jwtTokenProvider.createToken(user.getEmail(), user.getId(), user.getUsername());
    }
}