package af.gov.anar.identity_jwt.authentication.user.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import af.gov.anar.identity_jwt.authentication.auth.PasswordResetToken;
import af.gov.anar.identity_jwt.authentication.auth.PasswordResetTokenRepository;
import af.gov.anar.identity_jwt.authentication.environment.Environment;
import af.gov.anar.identity_jwt.authentication.environment.EnvironmentService;
import af.gov.anar.identity_jwt.authentication.group.Group;
import af.gov.anar.identity_jwt.authentication.group.GroupService;
import af.gov.anar.identity_jwt.authentication.profession.Profession;
import af.gov.anar.identity_jwt.authentication.profession.ProfessionRepository;
import af.gov.anar.identity_jwt.authentication.user.CustomUser;
import af.gov.anar.identity_jwt.authentication.user.User;
import af.gov.anar.identity_jwt.authentication.user.UserRepository;
import af.gov.anar.identity_jwt.authentication.user.UserService;

import af.gov.anar.identity_jwt.util.EmailUtil;
import af.gov.anar.identity_jwt.util.JsonParserUtil;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;

@Service
public class UserServiceImpl implements UserService {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProfessionRepository professionRepository;

	@Autowired
	private UserService userService;

	@Autowired
	private EmailUtil emailUtil;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private EnvironmentService environmentService;

	@Autowired
	private GroupService groupService;

	@Autowired
	private JsonParserUtil jsonParserUtil;

	public String encodePassword(String password) {
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		return passwordEncoder.encode(password);
	}

	@Override
	public User create(String userJson) {
		String defaultPassword = encodePassword("secret");
		User user = new User();

		Environment currentEnv = environmentService.findBySlug(getCurrentEnv());

		// Add env references in the user
		user.getEnvironments().add(currentEnv);
		// Add user reference in the env
		currentEnv.getUsers().add(user);
		try {
			if (jsonParserUtil.isValid(userJson)) {
				JSONObject jsonObj = jsonParserUtil.parse(userJson);

				if (jsonObj.has("name"))
					user.setName(jsonObj.isNull("name") ? "" : jsonObj.getString("name"));
				if (jsonObj.has("username"))
					user.setUsername(jsonObj.isNull("username") ? "" : jsonObj.getString("username").trim());
				if (jsonObj.has("phone_no"))
					user.setPhoneNo(jsonObj.isNull("phone_no") ? "" : jsonObj.getString("phone_no"));
				if (jsonObj.has("address"))
					user.setAddress(jsonObj.isNull("address") ? "" : jsonObj.getString("address"));
				if (jsonObj.has("email"))
					user.setEmail(jsonObj.isNull("email") ? "" : jsonObj.getString("email"));
				if (jsonObj.has("active"))
					user.setActive(jsonObj.isNull("active") ? false : jsonObj.getBoolean("active"));

				if (jsonObj.has("password")) {
					user.setPassword(jsonObj.isNull("password") ? "" : jsonObj.getString("password").trim());
					user.setOdkPassword(user.getPassword());
				}
				if (jsonObj.has("job")) {

					if (!jsonObj.getString("job").isEmpty()) {
						Long jobId = Long.valueOf(jsonObj.getString("job"));
						Profession userJob = this.professionRepository.findById(jobId).get();
						if (userJob != null) {
							user.setJob(userJob);
						} else {
							user.setJob(null);
						}

					}

				}

				if (jsonObj.has("confirm_password"))
					user.setConfirmPassword(
							jsonObj.isNull("confirm_password") ? "" : jsonObj.getString("confirm_password").trim());

				if (jsonObj.has("groups"))
					if (!jsonObj.isNull("groups")) {
						Set<Group> groups = new HashSet<>();

						JSONArray groupsArray = jsonObj.getJSONArray("groups");

						for (int i = 0; i < groupsArray.length(); i++) {
							JSONObject jObj = groupsArray.getJSONObject(i);
							Long groupId = jObj.getLong("id");
							groups.add(groupService.findById(groupId));
						}

						user.setGroups(groups);
					}

				if (user.isMatchingPasswords()) {
					String usersEncodedPassword = encodePassword(user.getPassword().trim());
					if (usersEncodedPassword == null) {
						user.setPassword(defaultPassword);
					} else {
						user.setPassword(usersEncodedPassword);
					}

					user = userRepository.save(user);
					return user;
				}
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return null;
	}

	@Override
	public User delete(Long id) {
		User project = findById(id);
		if (project != null) {
			userRepository.delete(project);
		}
		return project;
	}

	@Override
	public List<User> findAll() {
		return userRepository.findAll();
	}

	@Override
	public List<User> findAllByEnv(String envSlug) {
		List<User> users = userRepository.findAllByEnvSlug(envSlug);
		users.forEach(user -> {
			String odkPass = user.getOdkPassword();
			if (odkPass != null && odkPass.length() > 0) {
				user.setHasOdkPassword(true);
			}
		});

		return users;
	}

	@Override
	public User findById(Long id) {
		Optional<User> optionalObj = userRepository.findById(id);
		User user = optionalObj.get();

		String odkPass = user.getOdkPassword();
		if (odkPass != null && odkPass.length() > 0) {
			user.setHasOdkPassword(true);
		}
		return user;
	}

	@Override
	public User findByUsername(String username) {
		User user = userRepository.findByUsername(username);

		String odkPass = user.getOdkPassword();
		if (odkPass != null && odkPass.length() > 0) {
			user.setHasOdkPassword(true);
		}
		return user;
	}

	@Override
	public boolean update(Long id, String userJson) {
		System.out.println("UserServiceImp > update : " + id);

		if (id != null) {
		User user = findById(id);
	

		if (jsonParserUtil.isValid(userJson)) {
		JSONObject jsonObj = jsonParserUtil.parse(userJson);
		if (jsonObj.has("name"))
		user.setName(jsonObj.isNull("name") ? "" : jsonObj.getString("name"));
		if (jsonObj.has("username"))
		user.setUsername(jsonObj.isNull("username") ? "" :
		jsonObj.getString("username").trim());
		if (jsonObj.has("phone_no"))
		user.setPhoneNo(jsonObj.isNull("phone_no") ? "" :
		jsonObj.getString("phone_no"));
		if (jsonObj.has("address"))
		user.setAddress(jsonObj.isNull("address") ? "" :
		jsonObj.getString("address"));
		if (jsonObj.has("email"))
		user.setEmail(jsonObj.isNull("email") ? "" : jsonObj.getString("email"));
		if (jsonObj.has("active"))
		user.setActive(jsonObj.isNull("active") ? false :
		jsonObj.getBoolean("active"));
		if (jsonObj.has("job")) {

			if (!jsonObj.getString("job").isEmpty()) {
				Long jobId = Long.valueOf(jsonObj.getString("job"));
				Profession userProfession = this.professionRepository.findById(jobId).get();
				if (userProfession != null) {
					user.setJob(userProfession);
				} else {
					user.setJob(null);
				}

			}
			else {
				user.setJob(null);
			}
		}
		if (jsonObj.has("groups"))
		if (!jsonObj.isNull("groups")) {
		Set<Group> groups = new HashSet<>();

		JSONArray groupsArray = jsonObj.getJSONArray("groups");
		groupsArray.forEach(obj -> {
		JSONObject jObj = (JSONObject) obj;
		Long groupId = jObj.getLong("id");
		groups.add(groupService.findById(groupId));
		});

		user.setGroups(groups);
		}
		}

		userRepository.save(user);
		return true;
		}
		return false;
	}

	@Override
	public void updateAvatar(User user, String avatarFilename) {
		userRepository.updateAvatar(user.getUsername(), avatarFilename);
	}

	@Override
	public User getLoggedInUser() {
		return getLoggedInUser(false);
	}

	@Override
	public User getLoggedInUser(Boolean forceFresh) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String username = principal.toString();

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		return findByUsername(username);
	}

	@Override
	public String getSecurityContextHolderUsername(Boolean forceFresh) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String username = principal.toString();

		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		}

		return username;
	}

	@Override
	public boolean isAdmin() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		Collection<? extends GrantedAuthority> auths = ((UserDetails) principal).getAuthorities();
		if (auths.contains(new SimpleGrantedAuthority("ADMIN"))) {
			return true;
		}

		return false;

	}

	@Override
	public String getCurrentEnv() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUser userDetails = (CustomUser) authentication.getPrincipal();
		return userDetails.getCurrentEnv();

	}

	@Override
	public Profession findProfessionByUserId(Long userId) {
		return professionRepository.findById(userId).get();
	}

	@Override
	public List<Profession> findAllProfessionsByEnv(String envSlug) {
		return professionRepository.findByEnvSlug(envSlug);
	}

	@Override
	public long countByEnvSlug(String envSlug) {
		return userRepository.countByEnvSlug(envSlug);
	}

	@Override
	public long countByEnvSlugAndActive(String envSlug, Boolean active) {
		return userRepository.countByEnvSlugAndActive(envSlug, active);
	}

	@Override
	public User updatePreferences(String preferences) {
		// update the preferenes of currently logged-in user
		User user = getLoggedInUser();
		user.setPreferences(preferences);
		System.out.println("user updated:" + user.toString());
		return userRepository.save(user);

	}

	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Override
	public boolean updateUserPassword(String currentPassword, String newpassword) {
		User loggedInUser = getLoggedInUser();

		BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
		String newCurrentPassword = bCryptPasswordEncoder.encode(newpassword);

		if (passwordEncoder.matches(currentPassword, loggedInUser.getPassword())) {
			loggedInUser.setPassword(newCurrentPassword);
			userRepository.save(loggedInUser);
			return true;
		} else {
			return false;
		}

	}

	@Override
	public boolean updateUserOdkPassword(String currentPassword, String newPassword) {
		User loggedInUser = getLoggedInUser();

		if (currentPassword.equals(loggedInUser.getOdkPassword())) {
			loggedInUser.setOdkPassword(newPassword);
			userRepository.save(loggedInUser);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	@Override
	public void createPasswordResetTokenForUser(User user, String token, boolean active) {
		PasswordResetToken myToken = new PasswordResetToken(token, user, active);
		passwordResetTokenRepository.save(myToken);
	}

	@Override
	public boolean validatePasswordResetToken(String token) {
		PasswordResetToken passToken = passwordResetTokenRepository.findByToken(token);
		if (passToken == null || passToken.getActive() == false) {
			return false;
		}

		LocalDateTime time = LocalDateTime.now();
		if (passToken.getExpiryDate().plusMinutes(30).isBefore(time)){
			passToken.setActive(false);
			passwordResetTokenRepository.save(passToken);
			return false;
		}

		return true;
	}

	@Override
	public boolean createNewPassword(String newPassword, String confirmPassword, String token) {
		if (newPassword.equals(confirmPassword)) {
			PasswordResetToken userToken = passwordResetTokenRepository.findByToken(token);
			Long userId = userToken.getUser().getId();
			String newPass = encodePassword(newPassword);
			User user = userService.findById(userId);
			user.setPassword(newPass);
			userRepository.save(user);
			userToken.setActive(false);
			passwordResetTokenRepository.save(userToken);
			return true;
		}
		return false;
	}
	// Collection<? extends GrantedAuthority> getAuths
}
