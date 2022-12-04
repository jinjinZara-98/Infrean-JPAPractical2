package jpabook.jpashop.service;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.repository.MemberRepository;
import jpabook.jpashop.repository.MemberRepositoryOld;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * 회원 가입
     */
    @Transactional
    public Long join(Member member) {

        validateDuplicateMember(member); //중복 회원 검증
        memberRepository.save(member);
        return member.getId();
    }

    private void validateDuplicateMember(Member member) {
        //findByName는 공통화하지 못함, MemberRepository에서 따로 만듬
        List<Member> findMembers = memberRepository.findByName(member.getName());
        if (!findMembers.isEmpty()) {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        }
    }

    //회원 전체 조회
    public List<Member> findMembers() {

        return memberRepository.findAll();
    }

    //스프링 데이터 JPA는 Optional로 반환해주어
    public Member findOne(Long memberId) {
        return memberRepository.findById(memberId).get();
    }

    /**
     * 회원 수정
     */
    //수정 메서드 반환타입은 void
    //커맨드와 쿼리는 철저히 분리
    //수정은 변경인데 멤버를 쿼리하는 꼴
    //id를 가지고 조회하는 커맨드와 쿼리 같이 있는

    //업데이트는 변경성 메서드, 커맨드와 쿼리가 같이 있는 꼴
    //업데이트는 가급적이면 id값 정도만 반환하게끔
    //트랜잭션 시작
    @Transactional
    public void update(Long id, String name) {
        //@Transactional있는 상태에서 .findOne하면 영속성컨텍스트에서 가져옴
        //db애서 id에 맞는 Member객체 끌고오고, member는 영속상태
        Member member = memberRepository.findById(id).get();
        //영속상태의 member 이름을 바꿔줌
        //이 메서드 종료되면서 스프링 AOP가 동작하면서 @Transactional에 의해
        //트랜잭션 AOP가 끝나는 시점에 트랜잭션이 커밋이 됨
        member.setName(name);
    }

}
