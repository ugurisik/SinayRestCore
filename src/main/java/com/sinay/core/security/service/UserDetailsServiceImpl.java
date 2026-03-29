package com.sinay.core.security.service;

import com.sinay.core.core.ObjectCore;
import com.sinay.core.entity.QUser;
import com.sinay.core.entity.User;
import com.sinay.core.security.userdetails.AppUserDetails;
import com.querydsl.core.types.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Email veya username ile login destekleniyor
        QUser q = QUser.user;
       // Predicate emailPredicate = q.email.eq(username).and(q.visible.isTrue());
        //Predicate usernamePredicate = q.username.eq(username).and(q.visible.isTrue());

        Predicate userNameEmail = q.visible.isTrue().and(q.email.eq(username).or(q.username.eq(username)));

        ObjectCore.Result<User> emailUsernameResult = ObjectCore.findOne(q, userNameEmail);
       // ObjectCore.Result<User> usernameResult = ObjectCore.findOne(q, usernamePredicate);

        User user = emailUsernameResult.getDataOrNull();

       /* User user = emailResult.isSuccess()
                ? emailResult.getData()
                : usernameResult.isSuccess()
                        ? usernameResult.getData()
                        : null;
*/
        if (user == null) {
            throw new UsernameNotFoundException("Kullanıcı bulunamadı: " + username);
        }

        return AppUserDetails.from(user);
    }
}
