package com.softserveinc.dokazovi.service.impl;

import com.softserveinc.dokazovi.dto.payload.SignUpRequest;
import com.softserveinc.dokazovi.dto.user.UserDTO;
import com.softserveinc.dokazovi.entity.UserEntity;
import com.softserveinc.dokazovi.entity.VerificationToken;
import com.softserveinc.dokazovi.entity.enumerations.UserStatus;
import com.softserveinc.dokazovi.exception.BadRequestException;
import com.softserveinc.dokazovi.exception.EntityNotFoundException;
import com.softserveinc.dokazovi.mapper.UserMapper;
import com.softserveinc.dokazovi.pojo.UserSearchCriteria;
import com.softserveinc.dokazovi.repositories.UserRepository;
import com.softserveinc.dokazovi.repositories.VerificationTokenRepository;
import com.softserveinc.dokazovi.service.UserService;
import com.softserveinc.dokazovi.util.StringToNameParser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.transaction.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final VerificationTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;

	private static final String HAS_NO_DIRECTIONS = "hasNoDirections";
	private static final String HAS_NO_REGIONS = "hasNoRegions";
	private static final String HAS_NO_USERNAME = "hasNoUserName";

	@Override
	public UserEntity findByEmail(String email) {
		return userRepository.findByEmail(email).orElse(null);
	}

	@Override
	public Page<UserEntity> findAll(Pageable pageable) {
		return userRepository.findAll(pageable);
	}

	@Override
	public UserDTO findExpertById(Integer userId) {
		return userMapper.toUserDTO(userRepository.findById(userId).orElse(null));
	}

	@Override
	@Transactional
	public Page<UserDTO> findAllExperts(UserSearchCriteria userSearchCriteria, Pageable pageable) {

		if (validateParameters(userSearchCriteria, HAS_NO_DIRECTIONS, HAS_NO_REGIONS, HAS_NO_USERNAME)) {
			return userRepository.findDoctorsProfiles(pageable).map(userMapper::toUserDTO);
		}

		List<String> userName = userSearchCriteria.getUserNameList();

		if ((validateParameters(userSearchCriteria, HAS_NO_DIRECTIONS, HAS_NO_REGIONS)) && userName.size() == 1) {
			final String name = userName.get(0);
			return userRepository.findDoctorsByName(name, pageable).map(userMapper::toUserDTO);
		}

		if ((validateParameters(userSearchCriteria, HAS_NO_DIRECTIONS, HAS_NO_REGIONS)) && userName.size() == 2) {
			final String firstName = userName.get(0);
			final String lastName = userName.get(1);
			return userRepository.findDoctorsByName(firstName, lastName, pageable).map(userMapper::toUserDTO);
		}

		if ((validateParameters(userSearchCriteria, HAS_NO_DIRECTIONS, HAS_NO_USERNAME))) {
			return userRepository.findDoctorsProfilesByRegionsIds(
					userSearchCriteria.getRegions(), pageable)
					.map(userMapper::toUserDTO);
		}

		if ((validateParameters(userSearchCriteria, HAS_NO_REGIONS, HAS_NO_USERNAME))) {
			return userRepository.findDoctorsProfilesByDirectionsIds(
					userSearchCriteria.getDirections(), pageable)
					.map(userMapper::toUserDTO);
		}

		if ((validateParameters(userSearchCriteria, HAS_NO_USERNAME))) {
			return userRepository
					.findDoctorsProfiles(userSearchCriteria.getDirections(), userSearchCriteria.getRegions(), pageable)
					.map(userMapper::toUserDTO);
		}

		throw new EntityNotFoundException("Wrong search parameters");
	}

	private boolean validateParameters(UserSearchCriteria userSearchCriteria, String... args) {

		if (args.length == 3) {
			return !userSearchCriteria.hasName() && !userSearchCriteria.hasRegions() && !userSearchCriteria
					.hasDirections();
		}

		if (args.length == 2 && args[0].contains(HAS_NO_DIRECTIONS) && args[1].contains(HAS_NO_REGIONS)) {
			return !userSearchCriteria.hasRegions() && !userSearchCriteria.hasDirections();
		}

		if (args.length == 2 && args[0].contains(HAS_NO_DIRECTIONS) && args[1].contains(HAS_NO_USERNAME)) {
			return !userSearchCriteria.hasDirections() && !userSearchCriteria.hasName();
		}

		if (args.length == 2 && args[0].contains(HAS_NO_REGIONS) && args[1].contains(HAS_NO_USERNAME)) {

			return !userSearchCriteria.hasRegions() && !userSearchCriteria.hasName();
		}

		if (args.length == 1 && args[0].contains(HAS_NO_USERNAME)) {
			return !userSearchCriteria.hasName();
		}

		return false;
	}

	@Override
	public Page<UserDTO> findRandomExpertPreview(Set<Integer> directionsIds, Pageable pageable) {
		if (CollectionUtils.isEmpty(directionsIds)) {
			return userRepository.findRandomExperts(pageable)
					.map(userMapper::toUserDTO);
		}

		return userRepository.findRandomExpertsByDirectionsIdIn(directionsIds, pageable)
				.map(userMapper::toUserDTO);
	}

	@Override
	public void setEnableTrue(UserEntity user) {
		UserEntity userEntity = userRepository.findById(user.getId()).orElse(null);
		if (userEntity == null) {
			throw new BadRequestException("Something went wrong!!!");
		}
		userEntity.setEnabled(true);
		userRepository.save(userEntity);
	}

	@Override
	public VerificationToken getVerificationToken(String verificationToken) {
		return tokenRepository.findByToken(verificationToken);
	}

	@Override
	public void createVerificationToken(UserEntity user, String token) {
		VerificationToken myToken = VerificationToken.builder()
				.user(user)
				.token(token)
				.build();
		tokenRepository.save(myToken);
	}

	@Override
	public Boolean existsByEmail(String email) {
		return userRepository.existsByEmail(email);
	}

	@Override
	public UserEntity saveUser(UserEntity user) {
		return userRepository.save(user);
	}

	@Override
	public UserEntity registerNewUser(SignUpRequest signUpRequest) {
		UserEntity user = new UserEntity();
		StringToNameParser.setUserNameFromRequest(signUpRequest, user);
		user.setEmail(signUpRequest.getEmail());
		user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
		user.setStatus(UserStatus.NEW);
		user.setEnabled(false);
		return userRepository.save(user);
	}
}